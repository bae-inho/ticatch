package com.ticatch.admin.service.dto;

public record CanceledPerformanceResult(Long performanceId, int canceledReservations, int refundedPayments) {
}
