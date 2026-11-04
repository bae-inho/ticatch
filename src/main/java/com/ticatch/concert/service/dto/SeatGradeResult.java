package com.ticatch.concert.service.dto;

import com.ticatch.concert.domain.SeatGrade;

public record SeatGradeResult(Long id, String name, long price) {

    public static SeatGradeResult from(SeatGrade grade) {
        return new SeatGradeResult(grade.getId(), grade.getName(), grade.getPrice());
    }
}
