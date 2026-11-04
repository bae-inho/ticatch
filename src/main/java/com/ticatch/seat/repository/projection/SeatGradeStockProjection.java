package com.ticatch.seat.repository.projection;

/** 등급 하나의 살 수 있는 좌석 수. */
public record SeatGradeStockProjection(Long seatGradeId, Long availableCount) {
}
