package com.ticatch.seat.service;

import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.dto.PerformanceResult;
import com.ticatch.global.exception.BusinessException;
import com.ticatch.seat.domain.Seat;
import com.ticatch.seat.domain.SeatStatus;
import com.ticatch.seat.exception.SeatErrorCode;
import com.ticatch.seat.repository.SeatBulkRepository;
import com.ticatch.seat.repository.SeatBulkRepository.NewSeat;
import com.ticatch.seat.repository.SeatRepository;
import com.ticatch.seat.repository.projection.SeatGradeStockProjection;
import com.ticatch.seat.repository.projection.SeatProjection;
import com.ticatch.seat.service.dto.CreateSeatsCommand;
import com.ticatch.seat.service.dto.HeldSeatResult;
import com.ticatch.seat.service.dto.SeatGradeSalesResult;
import com.ticatch.seat.service.dto.SeatGradeStockResult;
import com.ticatch.seat.service.dto.SeatGradeStockResult.GradeStock;
import com.ticatch.seat.service.dto.SeatMapResult;
import com.ticatch.seat.service.dto.SeatMapResult.SeatItem;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatBulkRepository seatBulkRepository;
    private final SeatRepository seatRepository;
    private final ConcertService concertService;
    private final Clock clock;

    /** 등급마다 {@code {등급 이름}-{번호}} 이름으로 좌석을 만든다(예: VIP-1). 만든 좌석 수를 돌려준다. */
    @Transactional
    public int createSeats(CreateSeatsCommand command) {
        List<NewSeat> seats = command.grades().stream()
                .flatMap(grade -> IntStream.rangeClosed(1, grade.seatCount())
                        .mapToObj(number -> new NewSeat(grade.seatGradeId(), grade.gradeName() + "-" + number)))
                .toList();
        seatBulkRepository.insertAll(command.performanceId(), seats, Instant.now(clock));
        return seats.size();
    }

    /**
     * 회차의 좌석들을 모두 선점한다. 하나라도 없거나 이미 선점·판매됐으면 예외를 던지고, 트랜잭션이 되돌려져 아무것도 선점되지 않는다.
     * 살 수 있는 좌석만 바꾸는 조건부 UPDATE 한 문장으로 선점하고, 바뀐 행 수로 성공을 판단한다.
     */
    @Transactional
    public List<HeldSeatResult> hold(Long performanceId, List<Long> seatIds) {
        int held = seatRepository.holdAvailable(performanceId, seatIds, Instant.now(clock));
        if (held != seatIds.size()) {
            boolean allExist = seatRepository.countByPerformanceIdAndIdIn(performanceId, seatIds) == seatIds.size();
            throw new BusinessException(allExist ? SeatErrorCode.SEAT_NOT_AVAILABLE : SeatErrorCode.SEAT_NOT_FOUND);
        }
        return seatRepository.findAllById(seatIds).stream().map(HeldSeatResult::from).toList();
    }

    /** 선점을 푼다. 선점 취소와 만료에서 부른다. 이 좌석들을 가진 예매 행을 잠근 상태에서 부른다. */
    @Transactional
    public void release(List<Long> seatIds) {
        seatRepository.findAllById(seatIds).forEach(Seat::release);
    }

    /** 결제가 끝난 좌석을 판매 상태로 바꾼다. 예매 행을 잠근 상태에서 부른다. */
    @Transactional
    public void sell(List<Long> seatIds) {
        seatRepository.findAllById(seatIds).forEach(Seat::sell);
    }

    /** 환불된 좌석을 다시 팔 수 있게 한다. 예매 행을 잠근 상태에서 부른다. */
    @Transactional
    public void releaseSold(List<Long> seatIds) {
        seatRepository.findAllById(seatIds).forEach(Seat::releaseSold);
    }

    /** 회차의 등급별 전체 좌석 수와 판매된 좌석 수. 관리자 판매 현황에 쓴다. */
    public List<SeatGradeSalesResult> getSalesByGrade(Long performanceId) {
        return seatRepository.countSalesByGrade(performanceId, SeatStatus.SOLD).stream()
                .map(SeatGradeSalesResult::from)
                .toList();
    }

    /** 공개된 공연의 회차에 대해 등급별 가격과 남은 좌석 수를 준다. */
    public SeatGradeStockResult getSeatGradeStock(Long performanceId) {
        PerformanceResult performance = concertService.getOpenPerformance(performanceId);
        Map<Long, Long> available = seatRepository.countByGrade(performanceId, SeatStatus.AVAILABLE).stream()
                .collect(Collectors.toMap(
                        SeatGradeStockProjection::seatGradeId, SeatGradeStockProjection::availableCount));
        List<GradeStock> grades = performance.seatGrades().stream()
                .map(grade -> new GradeStock(
                        grade.id(), grade.name(), grade.price(), available.getOrDefault(grade.id(), 0L)))
                .toList();
        return new SeatGradeStockResult(performanceId, performance.onSale(), grades);
    }

    /** 공개된 공연의 회차 좌석 배치. {@code seatGradeId}가 null이면 모든 등급을 준다. */
    public SeatMapResult getSeatMap(Long performanceId, Long seatGradeId) {
        PerformanceResult performance = concertService.getOpenPerformance(performanceId);
        List<SeatProjection> seats = seatGradeId == null
                ? seatRepository.findSeatMap(performanceId)
                : seatRepository.findSeatMapByGrade(performanceId, seatGradeId);
        return new SeatMapResult(
                performanceId,
                performance.onSale(),
                seats.stream()
                        .map(seat -> new SeatItem(
                                seat.id(), seat.label(), seat.seatGradeId(), seat.status() == SeatStatus.AVAILABLE))
                        .toList());
    }
}
