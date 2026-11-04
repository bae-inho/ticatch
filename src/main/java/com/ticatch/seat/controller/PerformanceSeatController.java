package com.ticatch.seat.controller;

import com.ticatch.seat.controller.dto.SeatGradeStockResponse;
import com.ticatch.seat.controller.dto.SeatMapResponse;
import com.ticatch.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관람객용 회차 좌석 조회. 로그인 없이 부를 수 있다. */
@RestController
@RequestMapping("/api/v1/performances/{performanceId}")
@RequiredArgsConstructor
public class PerformanceSeatController {

    private final SeatService seatService;

    @GetMapping("/seat-grades")
    public SeatGradeStockResponse seatGrades(@PathVariable Long performanceId) {
        return SeatGradeStockResponse.from(seatService.getSeatGradeStock(performanceId));
    }

    @GetMapping("/seats")
    public SeatMapResponse seats(
            @PathVariable Long performanceId, @RequestParam(required = false) Long gradeId) {
        return SeatMapResponse.from(seatService.getSeatMap(performanceId, gradeId));
    }
}
