package com.ticatch.point.service.dto;

public record ChargePointCommand(Long userId, long amount, String idempotencyKey) {
}
