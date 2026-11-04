package com.ticatch.global.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 서버 안의 {@link Instant}를 API 응답용 한국 시간({@code +09:00})으로 바꾼다(ADR 0005). */
public final class KoreaTime {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private KoreaTime() {
    }

    public static OffsetDateTime of(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}
