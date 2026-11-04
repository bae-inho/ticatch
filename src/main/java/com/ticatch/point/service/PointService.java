package com.ticatch.point.service;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.web.IdempotencyErrorCode;
import com.ticatch.point.domain.PointTransaction;
import com.ticatch.point.domain.PointTransactionType;
import com.ticatch.point.domain.PointWallet;
import com.ticatch.point.repository.PointTransactionRepository;
import com.ticatch.point.repository.PointWalletRepository;
import com.ticatch.point.service.dto.ChargePointCommand;
import com.ticatch.point.service.dto.PointTransactionResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;

    /** 새 회원의 지갑을 잔액 0으로 만든다. 회원 가입과 같은 트랜잭션에서 부른다. */
    @Transactional
    public void openWallet(Long userId) {
        pointWalletRepository.save(PointWallet.open(userId));
    }

    public long getBalance(Long userId) {
        return pointWalletRepository.findByUserId(userId).orElseThrow().getBalance();
    }

    public Page<PointTransactionResult> getTransactions(Long userId, Pageable pageable) {
        Long walletId = pointWalletRepository.findByUserId(userId).orElseThrow().getId();
        return pointTransactionRepository.findAllByWalletId(walletId, pageable).map(PointTransactionResult::from);
    }

    /**
     * 포인트를 충전한다. 같은 멱등성 키로 다시 오면 충전하지 않고 처음 결과를 돌려준다(docs/api.md 1절).
     *
     * <p>지갑 행을 먼저 잠근 뒤 키를 확인한다. 같은 키의 요청이 동시에 와도 나중 요청은 잠금이 풀린 뒤
     * 앞 요청이 남긴 거래를 보게 되어, 충전은 한 번만 일어난다.
     */
    @Transactional
    public PointTransactionResult charge(ChargePointCommand command) {
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(command.userId()).orElseThrow();

        Optional<PointTransaction> previous = pointTransactionRepository.findByIdempotencyKey(command.idempotencyKey());
        if (previous.isPresent()) {
            if (!previous.get().isSameChargeAs(wallet.getId(), command.amount())) {
                throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return PointTransactionResult.from(previous.get());
        }

        long balanceAfter = wallet.charge(command.amount());
        PointTransaction transaction =
                PointTransaction.charge(wallet.getId(), command.amount(), balanceAfter, command.idempotencyKey());
        try {
            pointTransactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException exception) {
            // 다른 회원의 요청이 같은 키를 먼저 저장했다(uk_point_transaction_idempotency_key).
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return PointTransactionResult.from(transaction);
    }

    /** 결제 금액을 차감하고 차감 후 잔액을 돌려준다. 잔액이 모자라면 {@code INSUFFICIENT_POINTS}다. */
    @Transactional
    public long pay(Long userId, long amount, Long paymentId) {
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(userId).orElseThrow();
        long balanceAfter = wallet.pay(amount);
        pointTransactionRepository.save(PointTransaction.pay(wallet.getId(), amount, balanceAfter, paymentId));
        return balanceAfter;
    }

    /** 결제 금액을 돌려주고 환불 후 잔액을 돌려준다. */
    @Transactional
    public long refund(Long userId, long amount, Long paymentId) {
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(userId).orElseThrow();
        long balanceAfter = wallet.refund(amount);
        pointTransactionRepository.save(PointTransaction.refund(wallet.getId(), amount, balanceAfter, paymentId));
        return balanceAfter;
    }

    /** 결제했을 때의 차감 후 잔액. 같은 결제 요청이 다시 왔을 때 처음 응답을 그대로 만들기 위해 쓴다. */
    public long getBalanceAfterPayment(Long paymentId) {
        return pointTransactionRepository.findByPaymentIdAndType(paymentId, PointTransactionType.PAY)
                .orElseThrow()
                .getBalanceAfter();
    }
}
