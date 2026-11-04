package com.ticatch.concert.service.dto;

import com.ticatch.concert.domain.Concert;
import com.ticatch.concert.domain.ConcertStatus;
import java.time.Instant;

public record ConcertResult(Long id, String title, String description, ConcertStatus status, Instant createdAt) {

    public static ConcertResult from(Concert concert) {
        return new ConcertResult(
                concert.getId(),
                concert.getTitle(),
                concert.getDescription(),
                concert.getStatus(),
                concert.getCreatedAt());
    }
}
