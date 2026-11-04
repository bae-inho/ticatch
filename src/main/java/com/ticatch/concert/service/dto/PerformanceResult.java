package com.ticatch.concert.service.dto;

import com.ticatch.concert.domain.Performance;
import com.ticatch.concert.domain.PerformanceStatus;
import com.ticatch.concert.domain.Venue;
import java.time.Instant;
import java.util.List;

/** @param onSale 공연이 공개돼 있고 지금이 예매 기간인가 */
public record PerformanceResult(
        Long id,
        Long concertId,
        Long venueId,
        String venueName,
        Instant startsAt,
        Instant bookingOpensAt,
        PerformanceStatus status,
        boolean onSale,
        List<SeatGradeResult> seatGrades) {

    public static PerformanceResult of(Performance performance, Venue venue, boolean onSale) {
        return new PerformanceResult(
                performance.getId(),
                performance.getConcertId(),
                venue.getId(),
                venue.getName(),
                performance.getStartsAt(),
                performance.getBookingOpensAt(),
                performance.getStatus(),
                onSale,
                performance.getSeatGrades().stream().map(SeatGradeResult::from).toList());
    }
}
