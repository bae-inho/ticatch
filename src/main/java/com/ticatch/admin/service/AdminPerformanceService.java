package com.ticatch.admin.service;

import com.ticatch.admin.service.dto.CanceledPerformanceResult;
import com.ticatch.admin.service.dto.PerformanceSalesResult;
import com.ticatch.admin.service.dto.PerformanceSalesResult.GradeSales;
import com.ticatch.admin.service.dto.RegisterPerformanceCommand;
import com.ticatch.admin.service.dto.RegisteredPerformanceResult;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.dto.PerformanceResult;
import com.ticatch.concert.service.dto.SchedulePerformanceCommand;
import com.ticatch.concert.service.dto.SchedulePerformanceCommand.SeatGradeCommand;
import com.ticatch.concert.service.dto.SeatGradeResult;
import com.ticatch.payment.service.PaymentService;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.seat.service.SeatService;
import com.ticatch.seat.service.dto.CreateSeatsCommand;
import com.ticatch.seat.service.dto.CreateSeatsCommand.GradeSeats;
import com.ticatch.seat.service.dto.SeatGradeSalesResult;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 등록·취소·판매 현황은 공연, 좌석, 예매, 결제 모듈에 걸친 작업이라 여기서 묶는다(docs/domain.md 1절).
 */
@Service
@RequiredArgsConstructor
public class AdminPerformanceService {

    private final ConcertService concertService;
    private final SeatService seatService;
    private final ReservationService reservationService;
    private final PaymentService paymentService;

    /** 회차, 등급, 좌석을 한 트랜잭션으로 만든다. 좌석 생성이 실패하면 회차도 남지 않는다. */
    @Transactional
    public RegisteredPerformanceResult register(RegisterPerformanceCommand command) {
        PerformanceResult performance = concertService.schedulePerformance(new SchedulePerformanceCommand(
                command.concertId(),
                command.venueId(),
                command.startsAt(),
                command.bookingOpensAt(),
                command.seatGrades().stream().map(plan -> new SeatGradeCommand(plan.name(), plan.price())).toList()));

        Map<String, Long> gradeIds = performance.seatGrades().stream()
                .collect(Collectors.toMap(SeatGradeResult::name, SeatGradeResult::id));
        List<GradeSeats> gradeSeats = command.seatGrades().stream()
                .map(plan -> new GradeSeats(gradeIds.get(plan.name()), plan.name(), plan.seatCount()))
                .toList();
        int totalSeats = seatService.createSeats(new CreateSeatsCommand(performance.id(), gradeSeats));

        return new RegisteredPerformanceResult(performance.id(), totalSeats);
    }

    /**
     * 회차를 취소하고, 그 회차의 결제 대기·확정 예매를 모두 취소하고, 결제를 모두 환불한다. 한 트랜잭션이라 중간에
     * 실패하면 아무것도 바뀌지 않는다. 회차 행을 배타 잠금으로 먼저 잡아, 그동안 들어온 선점·결제·환불은
     * 취소가 끝난 뒤 취소된 회차를 보게 된다(ADR 0009).
     */
    @Transactional
    public CanceledPerformanceResult cancel(Long performanceId) {
        concertService.cancelPerformance(performanceId);
        int canceledReservations = reservationService.cancelAllOfPerformance(performanceId);
        int refundedPayments = paymentService.refundAllOfPerformance(performanceId);
        return new CanceledPerformanceResult(performanceId, canceledReservations, refundedPayments);
    }

    /**
     * 회차의 등급별 판매 좌석 수와 매출. 좌석 수와 매출을 같은 시점의 데이터로 세려고,
     * 트랜잭션 전체가 하나의 스냅숏을 보는 {@code REPEATABLE READ}로 읽는다.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PerformanceSalesResult getSales(Long performanceId) {
        PerformanceResult performance = concertService.getPerformance(performanceId);
        Map<Long, SeatGradeSalesResult> seats = seatService.getSalesByGrade(performanceId).stream()
                .collect(Collectors.toMap(SeatGradeSalesResult::seatGradeId, Function.identity()));
        List<GradeSales> grades = performance.seatGrades().stream()
                .map(grade -> new GradeSales(
                        grade.id(),
                        grade.name(),
                        grade.price(),
                        seats.get(grade.id()).totalSeats(),
                        seats.get(grade.id()).soldSeats()))
                .toList();
        return new PerformanceSalesResult(
                performanceId,
                grades.stream().mapToLong(GradeSales::totalSeats).sum(),
                grades.stream().mapToLong(GradeSales::soldSeats).sum(),
                paymentService.getRevenue(performanceId),
                grades);
    }
}
