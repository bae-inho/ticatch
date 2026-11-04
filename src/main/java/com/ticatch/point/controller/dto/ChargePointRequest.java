package com.ticatch.point.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChargePointRequest(
        @NotNull(message = "충전 금액을 입력해 주세요.")
        @Min(value = 1, message = "충전 금액은 1원 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "한 번에 1,000,000원까지 충전할 수 있습니다.")
        Long amount) {
}
