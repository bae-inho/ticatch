package com.ticatch.concert.service;

import com.ticatch.concert.domain.Venue;
import com.ticatch.concert.repository.VenueRepository;
import com.ticatch.concert.service.dto.VenueResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository venueRepository;

    @Transactional
    public VenueResult create(String name, String address) {
        return VenueResult.from(venueRepository.save(Venue.create(name, address)));
    }

    public Page<VenueResult> getVenues(Pageable pageable) {
        return venueRepository.findAll(pageable).map(VenueResult::from);
    }
}
