package com.ticatch.seat.domain;

import com.ticatch.global.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회차의 좌석 하나. 판매 상태의 원본이다(ADR 0003). 좌석은 회차 등록 때 JDBC 배치로 만든다. */
@Entity
@Table(name = "seat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long performanceId;

    @Column(nullable = false, updatable = false)
    private Long seatGradeId;

    @Column(nullable = false, updatable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;
}
