package com.ticatch.reservation.controller.dto;

import com.ticatch.global.web.KoreaTime;
import com.ticatch.reservation.domain.ReservationStatus;
import com.ticatch.reservation.service.dto.ReservationSummaryResult;
import java.time.OffsetDateTime;

public record ReservationSummaryResponse(
        Long reservationId,
        Long performanceId,
        String concertTitle,
        OffsetDateTime startsAt,
        ReservationStatus status,
        long totalAmount,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        Long paymentId) {

    public static ReservationSummaryResponse from(ReservationSummaryResult reservation) {
        return new ReservationSummaryResponse(
                reservation.id(),
                reservation.performanceId(),
                reservation.concertTitle(),
                KoreaTime.of(reservation.startsAt()),
                reservation.status(),
                reservation.totalAmount(),
                KoreaTime.of(reservation.expiresAt()),
                KoreaTime.of(reservation.createdAt()),
                reservation.paymentId());
    }
}
