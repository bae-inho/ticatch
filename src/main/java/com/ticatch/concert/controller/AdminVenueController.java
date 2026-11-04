package com.ticatch.concert.controller;

import com.ticatch.concert.controller.dto.CreateVenueRequest;
import com.ticatch.concert.controller.dto.VenueResponse;
import com.ticatch.concert.service.VenueService;
import com.ticatch.global.web.PageQuery;
import com.ticatch.global.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/venues")
@RequiredArgsConstructor
public class AdminVenueController {

    private final VenueService venueService;

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse venue = VenueResponse.from(venueService.create(request.name(), request.address()));
        return ResponseEntity.created(URI.create("/api/v1/admin/venues/" + venue.venueId())).body(venue);
    }

    @GetMapping
    public PageResponse<VenueResponse> list(@Valid PageQuery query) {
        return PageResponse.of(
                venueService.getVenues(query.toPageable(Sort.by(Sort.Direction.DESC, "id"))), VenueResponse::from);
    }
}
