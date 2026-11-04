package com.ticatch.reservation.domain;

/** 예매 상태(docs/domain.md 2절). {@code CANCELED}, {@code EXPIRED}는 끝 상태다. */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELED,
    EXPIRED
}
