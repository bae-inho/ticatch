package com.ticatch.concert.controller.dto;

import com.ticatch.concert.domain.ConcertStatus;
import com.ticatch.concert.domain.PerformanceStatus;
import com.ticatch.concert.service.dto.ConcertDetailResult;
import com.ticatch.concert.service.dto.PerformanceResult;
import com.ticatch.concert.service.dto.SeatGradeResult;
import com.ticatch.global.web.KoreaTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 관리자용 공연 상세. 상태와 관계없이 모든 회차를 보여 준다. */
public record AdminConcertDetailResponse(
        Long concertId,
        String title,
        String description,
        ConcertStatus status,
        OffsetDateTime createdAt,
        List<PerformanceItem> performances) {

    public static AdminConcertDetailResponse from(ConcertDetailResult detail) {
        return new AdminConcertDetailResponse(
                detail.concert().id(),
                detail.concert().title(),
                detail.concert().description(),
                detail.concert().status(),
                KoreaTime.of(detail.concert().createdAt()),
                detail.performances().stream().map(PerformanceItem::from).toList());
    }

    public record PerformanceItem(
            Long performanceId,
            Long venueId,
            String venueName,
            OffsetDateTime startsAt,
            OffsetDateTime bookingOpensAt,
            PerformanceStatus status,
            List<SeatGradeItem> seatGrades) {

        static PerformanceItem from(PerformanceResult performance) {
            return new PerformanceItem(
                    performance.id(),
                    performance.venueId(),
                    performance.venueName(),
                    KoreaTime.of(performance.startsAt()),
                    KoreaTime.of(performance.bookingOpensAt()),
                    performance.status(),
                    performance.seatGrades().stream().map(SeatGradeItem::from).toList());
        }
    }

    public record SeatGradeItem(Long seatGradeId, String name, long price) {

        static SeatGradeItem from(SeatGradeResult grade) {
            return new SeatGradeItem(grade.id(), grade.name(), grade.price());
        }
    }
}
