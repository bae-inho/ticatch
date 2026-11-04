package com.ticatch.reservation.service.dto;

import java.util.List;

public record HoldSeatsCommand(Long userId, Long performanceId, List<Long> seatIds) {
}
