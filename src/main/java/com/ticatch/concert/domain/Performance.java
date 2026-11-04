package com.ticatch.concert.domain;

import com.ticatch.concert.exception.ConcertErrorCode;
import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.jpa.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연의 회차 한 번. 좌석 등급({@link SeatGrade})을 함께 저장하는 애그리거트 루트다.
 * 공연과 공연장은 ID로만 가리킨다(ADR 0002).
 */
@Entity
@Table(name = "performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long concertId;

    @Column(nullable = false, updatable = false)
    private Long venueId;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant bookingOpensAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerformanceStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "performance_id", nullable = false, updatable = false)
    @OrderBy("id")
    private List<SeatGrade> seatGrades = new ArrayList<>();

    public static Performance schedule(Long concertId, Long venueId, Instant startsAt, Instant bookingOpensAt) {
        Performance performance = new Performance();
        performance.concertId = concertId;
        performance.venueId = venueId;
        performance.startsAt = startsAt;
        performance.bookingOpensAt = bookingOpensAt;
        performance.status = PerformanceStatus.SCHEDULED;
        return performance;
    }

    /**
     * 예매를 받을 수 있는 시간인가. 판매 가능 여부는 저장하지 않고 매번 계산한다(docs/domain.md 2절).
     * 공연이 공개됐는지는 {@link Concert}가 따로 판단한다.
     */
    public boolean isOnSale(Instant now) {
        return status == PerformanceStatus.SCHEDULED && !now.isBefore(bookingOpensAt) && now.isBefore(startsAt);
    }

    public SeatGrade addSeatGrade(String name, long price) {
        SeatGrade grade = SeatGrade.create(name, price);
        seatGrades.add(grade);
        return grade;
    }

    /** 회차를 취소한다. 예매 취소·환불은 이 회차를 잠근 같은 트랜잭션에서 이어서 한다. */
    public void cancel() {
        if (status == PerformanceStatus.CANCELED) {
            throw new BusinessException(ConcertErrorCode.PERFORMANCE_ALREADY_CANCELED);
        }
        status = PerformanceStatus.CANCELED;
    }
}
