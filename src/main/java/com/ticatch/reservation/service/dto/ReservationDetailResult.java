package com.ticatch.reservation.service.dto;

import com.ticatch.reservation.repository.projection.ReservationSeatProjection;
import java.util.List;

public record ReservationDetailResult(ReservationSummaryResult summary, List<SeatItem> seats) {

    public static ReservationDetailResult of(ReservationSummaryResult summary, List<ReservationSeatProjection> seats) {
        return new ReservationDetailResult(
                summary,
                seats.stream()
                        .map(seat -> new SeatItem(seat.seatId(), seat.label(), seat.gradeName(), seat.price()))
                        .toList());
    }

    public record SeatItem(Long seatId, String label, String gradeName, long price) {
    }
}
