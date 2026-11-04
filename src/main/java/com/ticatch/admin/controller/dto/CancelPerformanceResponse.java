package com.ticatch.admin.controller.dto;

import com.ticatch.admin.service.dto.CanceledPerformanceResult;

public record CancelPerformanceResponse(Long performanceId, int canceledReservations, int refundedPayments) {

    public static CancelPerformanceResponse from(CanceledPerformanceResult result) {
        return new CancelPerformanceResponse(
                result.performanceId(), result.canceledReservations(), result.refundedPayments());
    }
}
