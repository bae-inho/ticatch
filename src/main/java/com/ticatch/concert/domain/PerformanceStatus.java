package com.ticatch.concert.domain;

/** 회차 상태. 판매 중인지는 상태가 아니라 시각으로 계산한다(docs/domain.md 2절). */
public enum PerformanceStatus {
    SCHEDULED,
    CANCELED
}
