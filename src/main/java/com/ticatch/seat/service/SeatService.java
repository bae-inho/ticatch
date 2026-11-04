package com.ticatch.seat.service;

import com.ticatch.seat.repository.SeatBulkRepository;
import com.ticatch.seat.repository.SeatBulkRepository.NewSeat;
import com.ticatch.seat.service.dto.CreateSeatsCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatBulkRepository seatBulkRepository;
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
}
