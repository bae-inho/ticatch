package com.ticatch.point.service.dto;

public record RefundCommand(Long userId, long amount, Long paymentId) {
}
