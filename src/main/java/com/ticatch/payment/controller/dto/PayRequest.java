package com.ticatch.payment.controller.dto;

import jakarta.validation.constraints.NotNull;

public record PayRequest(@NotNull(message = "결제할 예매를 골라 주세요.") Long reservationId) {
}
