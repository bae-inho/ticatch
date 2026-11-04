package com.ticatch.concert.service;

import com.ticatch.concert.domain.Concert;
import com.ticatch.concert.domain.ConcertStatus;
import com.ticatch.concert.domain.Performance;
import com.ticatch.concert.domain.PerformanceStatus;
import com.ticatch.concert.domain.Venue;
import com.ticatch.concert.exception.ConcertErrorCode;
import com.ticatch.concert.repository.ConcertRepository;
import com.ticatch.concert.repository.PerformanceRepository;
import com.ticatch.concert.repository.VenueRepository;
import com.ticatch.concert.service.dto.ConcertDetailResult;
import com.ticatch.concert.service.dto.ConcertResult;
import com.ticatch.concert.service.dto.PerformanceResult;
import com.ticatch.concert.service.dto.SchedulePerformanceCommand;
import com.ticatch.global.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final PerformanceRepository performanceRepository;
    private final VenueRepository venueRepository;

    @Transactional
    public ConcertResult create(String title, String description) {
        return ConcertResult.from(concertRepository.save(Concert.create(title, description)));
    }

    /** {@code status}가 null이면 모든 상태의 공연을 준다. */
    public Page<ConcertResult> getConcerts(ConcertStatus status, Pageable pageable) {
        Page<Concert> concerts = status == null
                ? concertRepository.findAll(pageable)
                : concertRepository.findAllByStatus(status, pageable);
        return concerts.map(ConcertResult::from);
    }

    public ConcertDetailResult getDetail(Long concertId) {
        Concert concert = findConcert(concertId);
        List<Performance> performances = performanceRepository.findAllByConcertIdOrderByStartsAt(concertId);
        Map<Long, Venue> venues = venueRepository
                .findAllById(performances.stream().map(Performance::getVenueId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        return new ConcertDetailResult(
                ConcertResult.from(concert),
                performances.stream()
                        .map(performance -> PerformanceResult.of(performance, venues.get(performance.getVenueId())))
                        .toList());
    }

    @Transactional
    public ConcertResult update(Long concertId, String title, String description) {
        Concert concert = findConcert(concertId);
        concert.update(title, description);
        return ConcertResult.from(concert);
    }

    /** 예정된 회차가 하나 이상 있어야 공개할 수 있다(docs/requirements.md 5절). */
    @Transactional
    public void open(Long concertId) {
        Concert concert = findConcert(concertId);
        if (!performanceRepository.existsByConcertIdAndStatus(concertId, PerformanceStatus.SCHEDULED)) {
            throw new BusinessException(ConcertErrorCode.CONCERT_HAS_NO_PERFORMANCE);
        }
        concert.open();
    }

    @Transactional
    public void end(Long concertId) {
        findConcert(concertId).end();
    }

    @Transactional
    public PerformanceResult schedulePerformance(SchedulePerformanceCommand command) {
        findConcert(command.concertId()).requireNotEnded();
        Venue venue = venueRepository.findById(command.venueId())
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.VENUE_NOT_FOUND));

        Performance performance = Performance.schedule(
                command.concertId(), venue.getId(), command.startsAt(), command.bookingOpensAt());
        command.seatGrades().forEach(grade -> performance.addSeatGrade(grade.name(), grade.price()));
        return PerformanceResult.of(performanceRepository.saveAndFlush(performance), venue);
    }

    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.CONCERT_NOT_FOUND));
    }
}
