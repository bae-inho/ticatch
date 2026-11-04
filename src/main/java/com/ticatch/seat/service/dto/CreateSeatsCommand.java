package com.ticatch.seat.service.dto;

import java.util.List;

/** 회차의 등급마다 좌석을 몇 개 만들지. */
public record CreateSeatsCommand(Long performanceId, List<GradeSeats> grades) {

    public record GradeSeats(Long seatGradeId, String gradeName, int seatCount) {
    }
}
