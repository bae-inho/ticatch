package com.ticatch.seat.domain;

/** 좌석 판매 상태(docs/domain.md 2절, ADR 0003). */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    SOLD
}
