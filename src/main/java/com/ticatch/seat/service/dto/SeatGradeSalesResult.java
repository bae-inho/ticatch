package com.ticatch.seat.service.dto;

import com.ticatch.seat.repository.projection.SeatGradeSalesProjection;

public record SeatGradeSalesResult(Long seatGradeId, long totalSeats, long soldSeats) {

    public static SeatGradeSalesResult from(SeatGradeSalesProjection projection) {
        return new SeatGradeSalesResult(projection.seatGradeId(), projection.totalSeats(), projection.soldSeats());
    }
}
