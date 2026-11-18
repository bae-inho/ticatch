package com.ticatch.payment.service;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.web.IdempotencyErrorCode;
import com.ticatch.payment.domain.Payment;
import com.ticatch.payment.domain.PaymentStatus;
import com.ticatch.payment.exception.PaymentErrorCode;
import com.ticatch.payment.repository.PaymentRepository;
import com.ticatch.payment.repository.projection.RefundTargetProjection;
import com.ticatch.payment.service.dto.PayCommand;
import com.ticatch.payment.service.dto.PaymentResult;
import com.ticatch.point.service.PointService;
import com.ticatch.point.service.dto.RefundCommand;
import com.ticatch.reservation.service.ReservationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 결제와 환불(docs/domain.md 3절). 예매, 좌석, 결제, 포인트가 한 트랜잭션에서 함께 바뀐다.
 *
 * <p>잠금 순서: 결제는 예매 → 좌석 → 지갑, 환불은 회차(공유) → 결제 → 예매 → 좌석 → 지갑.
 * 지갑을 항상 마지막에 잠가 결제·환불·충전이 서로를 기다리는 교착 상태가 생기지 않게 한다(docs/domain.md 6절).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationService reservationService;
    private final PointService pointService;
    private final Clock clock;

    /**
     * 결제 대기 예매를 포인트로 결제해 확정한다. 같은 멱등성 키로 다시 오면 결제하지 않고 처음 결과를 돌려준다.
     *
     * <p>예매 행을 먼저 잠근 뒤 멱등성 키를 확인한다. 같은 요청이 동시에 와도 나중 요청은 앞 결제가 커밋된 뒤
     * 그 결제를 보게 되어 처음 결과를 받는다.
     */
    @Transactional
    public PaymentResult pay(PayCommand command) {
        reservationService.lockOwnReservation(command.userId(), command.reservationId());

        Optional<Payment> previous = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
        if (previous.isPresent()) {
            if (!previous.get().isSameRequestAs(command.userId(), command.reservationId())) {
                throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return PaymentResult.of(previous.get(), pointService.getBalanceAfterPayment(previous.get().getId()));
        }

        long amount = reservationService.confirm(command.userId(), command.reservationId());
        Payment payment = Payment.pay(
                command.reservationId(), command.userId(), amount, command.idempotencyKey(), Instant.now(clock));
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            // 다른 회원의 결제가 같은 키를 먼저 저장했다(uk_payment_idempotency_key).
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        long balanceAfter = pointService.pay(command.userId(), amount, payment.getId());
        return PaymentResult.of(payment, balanceAfter);
    }

    /** 결제를 전액 환불하고 예매를 취소한다. 공연 시작 24시간 전까지만 할 수 있다. */
    @Transactional
    public PaymentResult refund(Long userId, Long paymentId) {
        Long reservationId = paymentRepository.findReservationIdByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        reservationService.lockPerformanceOf(reservationId);
        Payment payment = paymentRepository.findWithLockById(paymentId).orElseThrow();
        payment.refund(Instant.now(clock));
        reservationService.cancelForRefund(reservationId);
        long balanceAfter = pointService.refund(userId, payment.getAmount(), payment.getId());
        return PaymentResult.of(payment, balanceAfter);
    }

    /**
     * 취소된 회차의 결제를 모두 전액 환불한다. 환불 마감과 관계없다. 환불한 건수를 돌려준다.
     * 회차 행을 배타 잠금으로 잡은 트랜잭션에서 부른다. 그동안 이 회차의 결제·환불은 회차 잠금에서 기다리므로
     * 결제 행을 따로 잠그지 않는다(ADR 0009).
     */
    @Transactional
    public int refundAllOfPerformance(Long performanceId) {
        List<RefundTargetProjection> targets = paymentRepository.findRefundTargets(performanceId);
        paymentRepository.refundAllByPerformanceId(performanceId, Instant.now(clock));
        pointService.refundAll(targets.stream()
                .map(target -> new RefundCommand(target.userId(), target.amount(), target.paymentId()))
                .toList());
        return targets.size();
    }

    /** 회차의 매출. 환불된 결제는 빼고 결제 완료 금액만 더한다. */
    @Transactional(readOnly = true)
    public long getRevenue(Long performanceId) {
        return paymentRepository.sumAmountByPerformanceIdAndStatus(performanceId, PaymentStatus.PAID);
    }
}
