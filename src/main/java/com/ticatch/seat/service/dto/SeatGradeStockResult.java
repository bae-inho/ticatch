package com.ticatch.seat.service.dto;

import java.util.List;

/** 회차의 등급별 가격과 남은 좌석 수. */
public record SeatGradeStockResult(Long performanceId, boolean onSale, List<GradeStock> seatGrades) {

    public record GradeStock(Long seatGradeId, String name, long price, long remainingSeats) {
    }
}
