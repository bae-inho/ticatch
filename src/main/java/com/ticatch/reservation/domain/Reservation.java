package com.ticatch.reservation.domain;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.jpa.BaseEntity;
import com.ticatch.reservation.exception.ReservationErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 회원이 한 회차에서 좌석을 사는 단위. 선점({@code PENDING})으로 시작한다(docs/domain.md 2절).
 * 회원과 회차는 ID로만 가리키고, 예매 좌석은 함께 저장한다(ADR 0002).
 */
@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {

    /** 선점 유효 시간(docs/requirements.md 5절). */
    public static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    /** 관람객은 공연 시작 이 시간 전까지만 확정된 예매를 취소할 수 있다(docs/requirements.md 5절). */
    public static final Duration REFUND_DEADLINE_BEFORE_START = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private Long performanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false, updatable = false)
    private long totalAmount;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    @OrderBy("seatId")
    private List<ReservationSeat> seats = new ArrayList<>();

    /**
     * 좌석을 선점한 결제 대기 예매를 만든다. 가격은 지금 등급 가격을 복사해 두고, 나중에 가격이 바뀌어도 이 예매의 금액은 그대로다.
     *
     * @param seatPrices 좌석 ID → 선점 시점 가격
     */
    public static Reservation hold(Long userId, Long performanceId, Map<Long, Long> seatPrices, Instant now) {
        Reservation reservation = new Reservation();
        reservation.userId = userId;
        reservation.performanceId = performanceId;
        reservation.status = ReservationStatus.PENDING;
        reservation.expiresAt = now.plus(HOLD_DURATION);
        seatPrices.forEach((seatId, price) -> reservation.seats.add(ReservationSeat.of(seatId, price)));
        reservation.totalAmount = seatPrices.values().stream().mapToLong(Long::longValue).sum();
        return reservation;
    }

    /** 결제 대기 중인 선점을 회원이 직접 취소한다. */
    public void cancel() {
        requirePending();
        status = ReservationStatus.CANCELED;
    }

    /** 선점 시간이 지난 예매를 만료시킨다. */
    public void expire() {
        requirePending();
        status = ReservationStatus.EXPIRED;
    }

    /**
     * 결제로 예매를 확정한다. 스케줄러가 아직 만료시키지 않았어도 선점 시간이 지났으면 실패한다.
     * 만료의 정확성은 스케줄러가 아니라 여기서 지킨다(docs/domain.md 7절).
     */
    public void confirm(Instant now) {
        requirePending();
        if (isOverdue(now)) {
            throw new BusinessException(ReservationErrorCode.RESERVATION_EXPIRED);
        }
        status = ReservationStatus.CONFIRMED;
    }

    /** 결제한 예매를 환불하며 취소한다. 확정된 예매에만 부른다. */
    public void cancelConfirmed() {
        if (status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 예매만 환불로 취소할 수 있다. reservationId=" + id + ", status=" + status);
        }
        status = ReservationStatus.CANCELED;
    }

    public boolean isOverdue(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public List<Long> seatIds() {
        return seats.stream().map(ReservationSeat::getSeatId).toList();
    }

    private void requirePending() {
        if (status != ReservationStatus.PENDING) {
            throw new BusinessException(ReservationErrorCode.RESERVATION_NOT_PENDING);
        }
    }
}
