package com.ticatch.concert.service.dto;

import com.ticatch.concert.domain.Venue;

public record VenueResult(Long id, String name, String address) {

    public static VenueResult from(Venue venue) {
        return new VenueResult(venue.getId(), venue.getName(), venue.getAddress());
    }
}
