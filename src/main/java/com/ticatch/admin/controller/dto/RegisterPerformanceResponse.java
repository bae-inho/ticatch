package com.ticatch.admin.controller.dto;

import com.ticatch.admin.service.dto.RegisteredPerformanceResult;

public record RegisterPerformanceResponse(Long performanceId, int totalSeats) {

    public static RegisterPerformanceResponse from(RegisteredPerformanceResult result) {
        return new RegisterPerformanceResponse(result.performanceId(), result.totalSeats());
    }
}
