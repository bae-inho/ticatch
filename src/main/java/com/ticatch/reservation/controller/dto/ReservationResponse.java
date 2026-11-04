package com.ticatch.reservation.controller.dto;

import com.ticatch.global.web.KoreaTime;
import com.ticatch.reservation.domain.ReservationStatus;
import com.ticatch.reservation.service.dto.ReservationDetailResult;
import java.time.OffsetDateTime;
import java.util.List;

/** 예매 상세. 선점 응답과 예매 상세 조회가 같은 모양을 쓴다. */
public record ReservationResponse(
        Long reservationId,
        Long performanceId,
        String concertTitle,
        OffsetDateTime startsAt,
        ReservationStatus status,
        long totalAmount,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        List<SeatItem> seats) {

    public static ReservationResponse from(ReservationDetailResult detail) {
        var summary = detail.summary();
        return new ReservationResponse(
                summary.id(),
                summary.performanceId(),
                summary.concertTitle(),
                KoreaTime.of(summary.startsAt()),
                summary.status(),
                summary.totalAmount(),
                KoreaTime.of(summary.expiresAt()),
                KoreaTime.of(summary.createdAt()),
                detail.seats().stream()
                        .map(seat -> new SeatItem(seat.seatId(), seat.label(), seat.gradeName(), seat.price()))
                        .toList());
    }

    public record SeatItem(Long seatId, String label, String gradeName, long price) {
    }
}
