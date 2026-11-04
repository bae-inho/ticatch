package com.ticatch;

import com.ticatch.admin.service.AdminPerformanceService;
import com.ticatch.admin.service.dto.RegisterPerformanceCommand;
import com.ticatch.admin.service.dto.RegisterPerformanceCommand.SeatGradePlan;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.VenueService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트용 공개 공연과 회차. 좌석은 VIP-1~3(150,000원), R-1~2(100,000원)다.
 * 테스트마다 새로 만들어 다른 테스트의 데이터와 섞이지 않게 한다.
 */
public class TestPerformances {

    private final VenueService venueService;
    private final ConcertService concertService;
    private final AdminPerformanceService adminPerformanceService;
    private final JdbcTemplate jdbc;

    public TestPerformances(
            VenueService venueService,
            ConcertService concertService,
            AdminPerformanceService adminPerformanceService,
            JdbcTemplate jdbc) {
        this.venueService = venueService;
        this.concertService = concertService;
        this.adminPerformanceService = adminPerformanceService;
        this.jdbc = jdbc;
    }

    /** 지금 예매할 수 있는 회차. */
    public Created onSale() {
        return create(Instant.now().minus(Duration.ofDays(1)));
    }

    /** 예매 오픈 전인 회차. */
    public Created notYetOnSale() {
        return create(Instant.now().plus(Duration.ofDays(1)));
    }

    private Created create(Instant bookingOpensAt) {
        long venueId = venueService.create("올림픽홀", "서울 송파구").id();
        long concertId = concertService.create("겨울 콘서트", null).id();
        long performanceId = adminPerformanceService.register(new RegisterPerformanceCommand(
                concertId,
                venueId,
                Instant.now().plus(Duration.ofDays(30)),
                bookingOpensAt,
                List.of(new SeatGradePlan("VIP", 150_000, 3), new SeatGradePlan("R", 100_000, 2))))
                .performanceId();
        concertService.open(concertId);

        Map<String, Long> seatIds = new HashMap<>();
        jdbc.query("SELECT label, id FROM seat WHERE performance_id = ?",
                row -> {
                    seatIds.put(row.getString("label"), row.getLong("id"));
                },
                performanceId);
        return new Created(performanceId, seatIds);
    }

    public record Created(long performanceId, Map<String, Long> seatIds) {

        public long seat(String label) {
            return seatIds.get(label);
        }
    }
}
