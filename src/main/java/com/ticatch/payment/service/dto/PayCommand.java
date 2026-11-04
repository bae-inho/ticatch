package com.ticatch.payment.service.dto;

public record PayCommand(Long userId, Long reservationId, String idempotencyKey) {
}
