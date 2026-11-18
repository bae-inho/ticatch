package com.ticatch.seat.domain;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.jpa.BaseEntity;
import com.ticatch.seat.exception.SeatErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    /** 낙관적 락. 같은 좌석을 동시에 바꾸면 나중에 커밋하는 쪽이 실패한다. */
    @Version
    private long version;

    /** 선점한다. 이미 선점·판매된 좌석이면 실패한다. 좌석 행을 잠근 상태에서 부른다. */
    public void hold() {
        if (status != SeatStatus.AVAILABLE) {
            throw new BusinessException(SeatErrorCode.SEAT_NOT_AVAILABLE);
        }
        status = SeatStatus.HELD;
    }

    /** 선점을 푼다(선점 취소, 만료). 선점 중인 좌석에만 부른다. */
    public void release() {
        if (status != SeatStatus.HELD) {
            throw new IllegalStateException("선점 중인 좌석만 풀 수 있다. seatId=" + id + ", status=" + status);
        }
        status = SeatStatus.AVAILABLE;
    }

    /** 결제가 끝나 판매한다. 선점 중인 좌석에만 부른다. */
    public void sell() {
        if (status != SeatStatus.HELD) {
            throw new IllegalStateException("선점 중인 좌석만 팔 수 있다. seatId=" + id + ", status=" + status);
        }
        status = SeatStatus.SOLD;
    }

    /** 환불돼 다시 팔 수 있게 한다. 판매된 좌석에만 부른다. */
    public void releaseSold() {
        if (status != SeatStatus.SOLD) {
            throw new IllegalStateException("판매된 좌석만 되돌릴 수 있다. seatId=" + id + ", status=" + status);
        }
        status = SeatStatus.AVAILABLE;
    }
}
