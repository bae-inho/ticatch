package com.ticatch.admin.service.dto;

import java.time.Instant;
import java.util.List;

public record RegisterPerformanceCommand(
        Long concertId,
        Long venueId,
        Instant startsAt,
        Instant bookingOpensAt,
        List<SeatGradePlan> seatGrades) {

    public record SeatGradePlan(String name, long price, int seatCount) {
    }
}
