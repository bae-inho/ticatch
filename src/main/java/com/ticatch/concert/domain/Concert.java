package com.ticatch.concert.domain;

import com.ticatch.concert.exception.ConcertErrorCode;
import com.ticatch.global.exception.BusinessException;
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

@Entity
@Table(name = "concert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConcertStatus status;

    public static Concert create(String title, String description) {
        Concert concert = new Concert();
        concert.title = title;
        concert.description = description;
        concert.status = ConcertStatus.PREPARING;
        return concert;
    }

    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** 관람객에게 공개한다. 회차가 있는지는 회차를 아는 서비스가 먼저 확인한다. */
    public void open() {
        requireStatus(ConcertStatus.PREPARING);
        status = ConcertStatus.OPEN;
    }

    public void end() {
        requireStatus(ConcertStatus.OPEN);
        status = ConcertStatus.ENDED;
    }

    /** 종료된 공연에는 회차를 더 붙일 수 없다. */
    public void requireNotEnded() {
        if (status == ConcertStatus.ENDED) {
            throw new BusinessException(ConcertErrorCode.CONCERT_INVALID_STATUS);
        }
    }

    private void requireStatus(ConcertStatus expected) {
        if (status != expected) {
            throw new BusinessException(ConcertErrorCode.CONCERT_INVALID_STATUS);
        }
    }
}
