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
import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

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

    /** 관람객용 목록. 공개된 공연만 준다. */
    public Page<ConcertResult> getOpenConcerts(Pageable pageable) {
        return concertRepository.findAllByStatus(ConcertStatus.OPEN, pageable).map(ConcertResult::from);
    }

    /** 관리자용 상세. 상태와 관계없이 보여 준다. */
    public ConcertDetailResult getDetail(Long concertId) {
        return detailOf(findConcert(concertId));
    }

    /** 관람객용 상세. 공개되지 않은 공연은 없는 것처럼 404다(docs/api.md 2절). */
    public ConcertDetailResult getOpenConcertDetail(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .filter(Concert::isOpen)
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.CONCERT_NOT_FOUND));
        return detailOf(concert);
    }

    /** 관람객이 볼 수 있는 회차. 공개되지 않은 공연의 회차는 없는 것처럼 404다. */
    public PerformanceResult getOpenPerformance(Long performanceId) {
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.PERFORMANCE_NOT_FOUND));
        Concert concert = concertRepository.findById(performance.getConcertId())
                .filter(Concert::isOpen)
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.PERFORMANCE_NOT_FOUND));
        Venue venue = venueRepository.getReferenceById(performance.getVenueId());
        return PerformanceResult.of(performance, venue, isOnSale(concert, performance));
    }

    /** 회차의 공연 시작 시각. 공개 여부와 관계없이 준다. 환불 마감을 계산할 때 쓴다. */
    public Instant getStartsAt(Long performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.PERFORMANCE_NOT_FOUND))
                .getStartsAt();
    }

    private ConcertDetailResult detailOf(Concert concert) {
        List<Performance> performances = performanceRepository.findAllByConcertIdOrderByStartsAt(concert.getId());
        Map<Long, Venue> venues = venueRepository
                .findAllById(performances.stream().map(Performance::getVenueId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        return new ConcertDetailResult(
                ConcertResult.from(concert),
                performances.stream()
                        .map(performance -> PerformanceResult.of(
                                performance, venues.get(performance.getVenueId()), isOnSale(concert, performance)))
                        .toList());
    }

    private boolean isOnSale(Concert concert, Performance performance) {
        return concert.isOpen() && performance.isOnSale(Instant.now(clock));
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
        Concert concert = findConcert(command.concertId());
        concert.requireNotEnded();
        Venue venue = venueRepository.findById(command.venueId())
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.VENUE_NOT_FOUND));

        Performance performance = Performance.schedule(
                command.concertId(), venue.getId(), command.startsAt(), command.bookingOpensAt());
        command.seatGrades().forEach(grade -> performance.addSeatGrade(grade.name(), grade.price()));
        performanceRepository.saveAndFlush(performance);
        return PerformanceResult.of(performance, venue, isOnSale(concert, performance));
    }

    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ConcertErrorCode.CONCERT_NOT_FOUND));
    }
}
