package com.ticatch.seat.controller.dto;

import com.ticatch.seat.service.dto.SeatMapResult;
import java.util.List;

public record SeatMapResponse(Long performanceId, boolean onSale, List<SeatItem> seats) {

    public static SeatMapResponse from(SeatMapResult result) {
        return new SeatMapResponse(
                result.performanceId(),
                result.onSale(),
                result.seats().stream()
                        .map(seat -> new SeatItem(seat.seatId(), seat.label(), seat.seatGradeId(), seat.available()))
                        .toList());
    }

    /** @param available 선점·판매되지 않은 좌석인가. 선점 중인지 판매됐는지는 구분하지 않는다(docs/api.md 3절). */
    public record SeatItem(Long seatId, String label, Long seatGradeId, boolean available) {
    }
}
