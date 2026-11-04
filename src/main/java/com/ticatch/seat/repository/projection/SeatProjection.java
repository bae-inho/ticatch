package com.ticatch.seat.repository.projection;

import com.ticatch.seat.domain.SeatStatus;

/** 좌석 배치도 한 칸. 엔티티 대신 필요한 컬럼만 읽는다. */
public record SeatProjection(Long id, String label, Long seatGradeId, SeatStatus status) {
}
