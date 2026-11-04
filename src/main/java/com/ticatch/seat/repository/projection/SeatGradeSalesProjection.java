package com.ticatch.seat.repository.projection;

/** 등급 하나의 전체 좌석 수와 판매된 좌석 수. */
public record SeatGradeSalesProjection(Long seatGradeId, Long totalSeats, Long soldSeats) {
}
