package com.ticatch.seat.controller.dto;

import com.ticatch.seat.service.dto.SeatGradeStockResult;
import java.util.List;

public record SeatGradeStockResponse(Long performanceId, boolean onSale, List<SeatGradeItem> seatGrades) {

    public static SeatGradeStockResponse from(SeatGradeStockResult result) {
        return new SeatGradeStockResponse(
                result.performanceId(),
                result.onSale(),
                result.seatGrades().stream()
                        .map(grade -> new SeatGradeItem(
                                grade.seatGradeId(), grade.name(), grade.price(), grade.remainingSeats()))
                        .toList());
    }

    public record SeatGradeItem(Long seatGradeId, String name, long price, long remainingSeats) {
    }
}
