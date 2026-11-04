package com.ticatch.concert.service.dto;

import java.util.List;

public record ConcertDetailResult(ConcertResult concert, List<PerformanceResult> performances) {
}
