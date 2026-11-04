package com.ticatch.admin.service;

import com.ticatch.admin.service.dto.RegisterPerformanceCommand;
import com.ticatch.admin.service.dto.RegisteredPerformanceResult;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.dto.PerformanceResult;
import com.ticatch.concert.service.dto.SchedulePerformanceCommand;
import com.ticatch.concert.service.dto.SchedulePerformanceCommand.SeatGradeCommand;
import com.ticatch.concert.service.dto.SeatGradeResult;
import com.ticatch.seat.service.SeatService;
import com.ticatch.seat.service.dto.CreateSeatsCommand;
import com.ticatch.seat.service.dto.CreateSeatsCommand.GradeSeats;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회차 등록은 공연 모듈(회차·등급)과 좌석 모듈(좌석)에 걸친 작업이라 여기서 묶는다(docs/domain.md 1절). */
@Service
@RequiredArgsConstructor
public class AdminPerformanceService {

    private final ConcertService concertService;
    private final SeatService seatService;

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
}
