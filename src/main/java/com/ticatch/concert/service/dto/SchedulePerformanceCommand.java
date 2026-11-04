package com.ticatch.concert.service.dto;

import java.time.Instant;
import java.util.List;

public record SchedulePerformanceCommand(
        Long concertId,
        Long venueId,
        Instant startsAt,
        Instant bookingOpensAt,
        List<SeatGradeCommand> seatGrades) {

    public record SeatGradeCommand(String name, long price) {
    }
}
