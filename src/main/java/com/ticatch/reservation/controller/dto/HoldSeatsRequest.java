package com.ticatch.reservation.controller.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

public record HoldSeatsRequest(
        @NotNull(message = "회차를 골라 주세요.")
        Long performanceId,

        @NotNull(message = "좌석을 골라 주세요.")
        @Size(min = 1, max = 4, message = "좌석은 1개 이상 4개 이하로 골라야 합니다.")
        List<@NotNull(message = "좌석 ID가 비어 있습니다.") Long> seatIds) {

    @AssertTrue(message = "같은 좌석을 두 번 고를 수 없습니다.")
    public boolean isSeatIdsUnique() {
        return seatIds == null || new HashSet<>(seatIds).size() == seatIds.size();
    }
}
