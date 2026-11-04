package com.ticatch.admin.service.dto;

import java.util.List;

/** @param revenue 환불되지 않은 결제 금액의 합 */
public record PerformanceSalesResult(
        Long performanceId, long totalSeats, long soldSeats, long revenue, List<GradeSales> seatGrades) {

    public record GradeSales(Long seatGradeId, String name, long price, long totalSeats, long soldSeats) {
    }
}
