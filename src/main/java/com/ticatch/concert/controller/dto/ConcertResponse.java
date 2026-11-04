package com.ticatch.concert.controller.dto;

import com.ticatch.concert.domain.ConcertStatus;
import com.ticatch.concert.service.dto.ConcertResult;
import com.ticatch.global.web.KoreaTime;
import java.time.OffsetDateTime;

public record ConcertResponse(
        Long concertId, String title, String description, ConcertStatus status, OffsetDateTime createdAt) {

    public static ConcertResponse from(ConcertResult concert) {
        return new ConcertResponse(
                concert.id(),
                concert.title(),
                concert.description(),
                concert.status(),
                KoreaTime.of(concert.createdAt()));
    }
}
