package com.ticatch.concert.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVenueRequest(
        @NotBlank(message = "공연장 이름을 입력해 주세요.")
        @Size(max = 100, message = "공연장 이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
        String address) {
}
