package com.ticatch.seat.service.dto;

import java.util.List;

/** 회차의 좌석 배치. 좌석마다 지금 살 수 있는지만 알려 준다. */
public record SeatMapResult(Long performanceId, boolean onSale, List<SeatItem> seats) {

    public record SeatItem(Long seatId, String label, Long seatGradeId, boolean available) {
    }
}
