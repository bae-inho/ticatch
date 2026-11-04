package com.ticatch.seat.service.dto;

import com.ticatch.seat.domain.Seat;

public record HeldSeatResult(Long seatId, String label, Long seatGradeId) {

    public static HeldSeatResult from(Seat seat) {
        return new HeldSeatResult(seat.getId(), seat.getLabel(), seat.getSeatGradeId());
    }
}
