package com.ticatch.reservation.service;

import com.ticatch.concert.exception.ConcertErrorCode;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.dto.PerformanceResult;
import com.ticatch.concert.service.dto.SeatGradeResult;
import com.ticatch.global.exception.BusinessException;
import com.ticatch.reservation.domain.Reservation;
import com.ticatch.reservation.domain.ReservationStatus;
import com.ticatch.reservation.exception.ReservationErrorCode;
import com.ticatch.reservation.repository.ReservationRepository;
import com.ticatch.reservation.service.dto.HoldSeatsCommand;
import com.ticatch.reservation.service.dto.ReservationDetailResult;
import com.ticatch.reservation.service.dto.ReservationSummaryResult;
import com.ticatch.seat.service.SeatService;
import com.ticatch.seat.service.dto.HeldSeatResult;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 좌석 선점, 선점 취소, 만료, 내 예매 조회(docs/domain.md 3절). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ConcertService concertService;
    private final SeatService seatService;
    private final Clock clock;

    /**
     * 좌석을 선점해 결제 대기 예매를 만든다. 좌석 선점과 예매 생성은 한 트랜잭션이라, 어느 하나가 실패하면 둘 다 남지 않는다.
     *
     * <p>회원·회차당 결제 대기 예매는 하나다. 먼저 확인하고, 같은 회원의 요청이 동시에 와서 확인을 함께 통과하면
     * 조건부 유일 인덱스({@code uk_reservation_pending_user_performance})가 두 번째 저장을 막는다.
     */
    @Transactional
    public ReservationDetailResult hold(HoldSeatsCommand command) {
        Instant now = Instant.now(clock);
        PerformanceResult performance = concertService.getOpenPerformance(command.performanceId());
        if (!performance.onSale()) {
            throw new BusinessException(ConcertErrorCode.PERFORMANCE_NOT_ON_SALE);
        }
        expireOwnStalePending(command.userId(), command.performanceId(), now);

        List<HeldSeatResult> seats = seatService.hold(command.performanceId(), command.seatIds());
        Map<Long, Long> gradePrices = performance.seatGrades().stream()
                .collect(Collectors.toMap(SeatGradeResult::id, SeatGradeResult::price));
        Map<Long, Long> seatPrices = new LinkedHashMap<>();
        seats.forEach(seat -> seatPrices.put(seat.seatId(), gradePrices.get(seat.seatGradeId())));

        Reservation reservation = Reservation.hold(command.userId(), command.performanceId(), seatPrices, now);
        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ReservationErrorCode.PENDING_RESERVATION_EXISTS);
        }
        return getMyReservation(command.userId(), reservation.getId());
    }

    /** 결제 전 선점을 취소하고 좌석을 돌려놓는다. */
    @Transactional
    public void cancel(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        reservation.cancel();
        seatService.release(reservation.seatIds());
    }

    /**
     * 선점 시간이 지난 결제 대기 예매를 최대 {@code limit}건 만료시키고 좌석을 돌려놓는다. 만료시킨 건수를 돌려준다.
     * 스케줄러가 주기적으로 부른다.
     */
    @Transactional
    public int expireOverdue(Instant now, int limit) {
        List<Reservation> overdue = reservationRepository.findOverdueForUpdate(now, limit);
        overdue.forEach(this::expire);
        return overdue.size();
    }

    public Page<ReservationSummaryResult> getMyReservations(Long userId, ReservationStatus status, Pageable pageable) {
        return (status == null
                ? reservationRepository.findSummaries(userId, pageable)
                : reservationRepository.findSummariesByStatus(userId, status, pageable))
                .map(ReservationSummaryResult::from);
    }

    public ReservationDetailResult getMyReservation(Long userId, Long reservationId) {
        ReservationSummaryResult summary = reservationRepository.findSummary(reservationId, userId)
                .map(ReservationSummaryResult::from)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        return ReservationDetailResult.of(summary, reservationRepository.findSeats(reservationId));
    }

    /**
     * 이 회원의 결제 대기 예매가 있으면 새로 선점할 수 없다. 단, 시간이 지났는데 스케줄러가 아직 만료시키지 않은
     * 예매라면 여기서 바로 만료시킨다. 스케줄러를 기다리느라 원래 주인이 다시 고르지 못하는 일을 막는다.
     */
    private void expireOwnStalePending(Long userId, Long performanceId, Instant now) {
        reservationRepository.findByUserIdAndPerformanceIdAndStatus(userId, performanceId, ReservationStatus.PENDING)
                .ifPresent(pending -> {
                    if (!pending.isOverdue(now)) {
                        throw new BusinessException(ReservationErrorCode.PENDING_RESERVATION_EXISTS);
                    }
                    expire(pending);
                    // Hibernate는 INSERT를 UPDATE보다 먼저 보낸다. 새 예매를 넣기 전에 이 만료를 DB에 반영해야
                    // 결제 대기 예매가 잠시 두 개가 되어 유일 인덱스에 걸리지 않는다.
                    reservationRepository.flush();
                });
    }

    private void expire(Reservation reservation) {
        reservation.expire();
        seatService.release(reservation.seatIds());
    }
}
