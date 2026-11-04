package com.ticatch.admin.controller.dto;

import com.ticatch.admin.service.dto.PerformanceSalesResult;
import java.util.List;

public record PerformanceSalesResponse(
        Long performanceId, long totalSeats, long soldSeats, long revenue, List<GradeSalesItem> seatGrades) {

    public static PerformanceSalesResponse from(PerformanceSalesResult result) {
        return new PerformanceSalesResponse(
                result.performanceId(),
                result.totalSeats(),
                result.soldSeats(),
                result.revenue(),
                result.seatGrades().stream()
                        .map(grade -> new GradeSalesItem(
                                grade.seatGradeId(), grade.name(), grade.price(), grade.totalSeats(), grade.soldSeats()))
                        .toList());
    }

    public record GradeSalesItem(Long seatGradeId, String name, long price, long totalSeats, long soldSeats) {
    }
}
