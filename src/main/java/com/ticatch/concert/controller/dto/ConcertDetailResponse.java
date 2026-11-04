package com.ticatch.concert.controller.dto;

import com.ticatch.concert.domain.PerformanceStatus;
import com.ticatch.concert.service.dto.ConcertDetailResult;
import com.ticatch.concert.service.dto.PerformanceResult;
import com.ticatch.global.web.KoreaTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 관람객용 공연 상세. 가격과 잔여 좌석은 회차별 등급 API로 따로 조회한다. */
public record ConcertDetailResponse(
        Long concertId, String title, String description, List<PerformanceItem> performances) {

    public static ConcertDetailResponse from(ConcertDetailResult detail) {
        return new ConcertDetailResponse(
                detail.concert().id(),
                detail.concert().title(),
                detail.concert().description(),
                detail.performances().stream().map(PerformanceItem::from).toList());
    }

    /** @param onSale 지금 예매할 수 있는가 */
    public record PerformanceItem(
            Long performanceId,
            String venueName,
            OffsetDateTime startsAt,
            OffsetDateTime bookingOpensAt,
            PerformanceStatus status,
            boolean onSale) {

        static PerformanceItem from(PerformanceResult performance) {
            return new PerformanceItem(
                    performance.id(),
                    performance.venueName(),
                    KoreaTime.of(performance.startsAt()),
                    KoreaTime.of(performance.bookingOpensAt()),
                    performance.status(),
                    performance.onSale());
        }
    }
}
