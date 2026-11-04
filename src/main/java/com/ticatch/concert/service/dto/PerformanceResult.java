package com.ticatch.concert.service.dto;

import com.ticatch.concert.domain.Performance;
import com.ticatch.concert.domain.PerformanceStatus;
import com.ticatch.concert.domain.Venue;
import java.time.Instant;
import java.util.List;

public record PerformanceResult(
        Long id,
        Long venueId,
        String venueName,
        Instant startsAt,
        Instant bookingOpensAt,
        PerformanceStatus status,
        List<SeatGradeResult> seatGrades) {

    public static PerformanceResult of(Performance performance, Venue venue) {
        return new PerformanceResult(
                performance.getId(),
                venue.getId(),
                venue.getName(),
                performance.getStartsAt(),
                performance.getBookingOpensAt(),
                performance.getStatus(),
                performance.getSeatGrades().stream().map(SeatGradeResult::from).toList());
    }
}
