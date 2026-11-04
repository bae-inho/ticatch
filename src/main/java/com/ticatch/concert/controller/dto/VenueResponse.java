package com.ticatch.concert.controller.dto;

import com.ticatch.concert.service.dto.VenueResult;

public record VenueResponse(Long venueId, String name, String address) {

    public static VenueResponse from(VenueResult venue) {
        return new VenueResponse(venue.id(), venue.name(), venue.address());
    }
}
