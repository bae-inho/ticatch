package com.ticatch.reservation.controller;

import com.ticatch.global.web.PageQuery;
import com.ticatch.global.web.PageResponse;
import com.ticatch.reservation.controller.dto.HoldSeatsRequest;
import com.ticatch.reservation.controller.dto.ReservationResponse;
import com.ticatch.reservation.controller.dto.ReservationSummaryResponse;
import com.ticatch.reservation.domain.ReservationStatus;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.reservation.service.dto.HoldSeatsCommand;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/api/v1/reservations")
    public ResponseEntity<ReservationResponse> hold(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HoldSeatsRequest request) {
        ReservationResponse reservation = ReservationResponse.from(reservationService.hold(
                new HoldSeatsCommand(userId(jwt), request.performanceId(), request.seatIds())));
        return ResponseEntity.created(URI.create("/api/v1/me/reservations/" + reservation.reservationId()))
                .body(reservation);
    }

    @PostMapping("/api/v1/reservations/{reservationId}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reservationId) {
        reservationService.cancel(userId(jwt), reservationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/me/reservations")
    public PageResponse<ReservationSummaryResponse> myReservations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ReservationStatus status,
            @Valid PageQuery query) {
        return PageResponse.of(
                reservationService.getMyReservations(
                        userId(jwt), status, query.toPageable(Sort.by(Sort.Direction.DESC, "id"))),
                ReservationSummaryResponse::from);
    }

    @GetMapping("/api/v1/me/reservations/{reservationId}")
    public ReservationResponse myReservation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reservationId) {
        return ReservationResponse.from(reservationService.getMyReservation(userId(jwt), reservationId));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
