package com.ticatch.reservation.domain;

import com.ticatch.global.jpa.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 예매에 포함된 좌석 하나와 선점 시점의 가격. {@link Reservation} 애그리거트 안에 있다. */
@Entity
@Table(name = "reservation_seat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long seatId;

    @Column(nullable = false, updatable = false)
    private long price;

    static ReservationSeat of(Long seatId, long price) {
        ReservationSeat seat = new ReservationSeat();
        seat.seatId = seatId;
        seat.price = price;
        return seat;
    }
}
