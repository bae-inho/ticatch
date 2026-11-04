package com.ticatch.concert.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공연 등록과 수정에 쓴다. 수정할 때도 두 필드를 모두 보낸다. */
public record ConcertRequest(
        @NotBlank(message = "공연 제목을 입력해 주세요.")
        @Size(max = 100, message = "공연 제목은 100자 이하여야 합니다.")
        String title,

        @Size(max = 2000, message = "공연 설명은 2,000자 이하여야 합니다.")
        String description) {
}
