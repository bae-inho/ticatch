package com.ticatch.reservation.repository.projection;

import com.ticatch.reservation.domain.ReservationStatus;
import java.time.Instant;

/** 내 예매 목록 한 줄. 공연 제목과 공연 시각은 공연 모듈 테이블을 조인해 읽는다(ADR 0002, 조회 전용). */
public record ReservationSummaryProjection(
        Long id,
        Long performanceId,
        String concertTitle,
        Instant startsAt,
        ReservationStatus status,
        Long totalAmount,
        Instant expiresAt,
        Instant createdAt) {
}
