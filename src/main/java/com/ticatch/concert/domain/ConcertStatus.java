package com.ticatch.concert.domain;

/** 공연 상태. {@code PREPARING → OPEN → ENDED} 순서로만 바뀐다(docs/domain.md 2절). */
public enum ConcertStatus {
    PREPARING,
    OPEN,
    ENDED
}
