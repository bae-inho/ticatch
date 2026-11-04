package com.ticatch.reservation.repository.projection;

/** 예매 좌석 하나. 좌석 이름과 등급 이름은 좌석·공연 모듈 테이블을 조인해 읽는다(조회 전용). */
public record ReservationSeatProjection(Long seatId, String label, String gradeName, Long price) {
}
