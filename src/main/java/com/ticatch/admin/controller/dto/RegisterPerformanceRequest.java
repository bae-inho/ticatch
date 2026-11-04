package com.ticatch.admin.controller.dto;

import com.ticatch.admin.service.dto.RegisterPerformanceCommand;
import com.ticatch.admin.service.dto.RegisterPerformanceCommand.SeatGradePlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record RegisterPerformanceRequest(
        @NotNull(message = "공연장을 골라 주세요.")
        Long venueId,

        @NotNull(message = "공연 시작 시각을 입력해 주세요.")
        @Future(message = "공연 시작 시각은 지금 이후여야 합니다.")
        OffsetDateTime startsAt,

        @NotNull(message = "예매 오픈 시각을 입력해 주세요.")
        OffsetDateTime bookingOpensAt,

        @NotNull(message = "좌석 등급을 입력해 주세요.")
        @Size(min = 1, max = 10, message = "좌석 등급은 1개 이상 10개 이하여야 합니다.")
        List<@Valid SeatGradeRequest> seatGrades) {

    @AssertTrue(message = "예매 오픈 시각은 공연 시작 시각보다 앞서야 합니다.")
    public boolean isBookingOpensBeforeStart() {
        return startsAt == null || bookingOpensAt == null || bookingOpensAt.isBefore(startsAt);
    }

    @AssertTrue(message = "한 회차 안에서 등급 이름이 겹치면 안 됩니다.")
    public boolean isSeatGradeNamesUnique() {
        if (seatGrades == null) {
            return true;
        }
        List<String> names = seatGrades.stream().filter(Objects::nonNull).map(SeatGradeRequest::name).toList();
        return names.stream().distinct().count() == names.size();
    }

    public RegisterPerformanceCommand toCommand(Long concertId) {
        return new RegisterPerformanceCommand(
                concertId,
                venueId,
                startsAt.toInstant(),
                bookingOpensAt.toInstant(),
                seatGrades.stream()
                        .map(grade -> new SeatGradePlan(grade.name(), grade.price(), grade.seatCount()))
                        .toList());
    }

    public record SeatGradeRequest(
            @NotBlank(message = "등급 이름을 입력해 주세요.")
            @Size(max = 10, message = "등급 이름은 10자 이하여야 합니다.")
            String name,

            @NotNull(message = "가격을 입력해 주세요.")
            @Min(value = 1, message = "가격은 1원 이상이어야 합니다.")
            @Max(value = 10_000_000, message = "가격은 10,000,000원 이하여야 합니다.")
            Long price,

            @NotNull(message = "좌석 수를 입력해 주세요.")
            @Min(value = 1, message = "좌석 수는 1개 이상이어야 합니다.")
            @Max(value = 5_000, message = "등급별 좌석 수는 5,000개 이하여야 합니다.")
            Integer seatCount) {
    }
}
