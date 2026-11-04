package com.ticatch.reservation.service.dto;

import com.ticatch.reservation.domain.ReservationStatus;
import com.ticatch.reservation.repository.projection.ReservationSummaryProjection;
import java.time.Instant;

public record ReservationSummaryResult(
        Long id,
        Long performanceId,
        String concertTitle,
        Instant startsAt,
        ReservationStatus status,
        long totalAmount,
        Instant expiresAt,
        Instant createdAt) {

    public static ReservationSummaryResult from(ReservationSummaryProjection row) {
        return new ReservationSummaryResult(
                row.id(),
                row.performanceId(),
                row.concertTitle(),
                row.startsAt(),
                row.status(),
                row.totalAmount(),
                row.expiresAt(),
                row.createdAt());
    }
}
