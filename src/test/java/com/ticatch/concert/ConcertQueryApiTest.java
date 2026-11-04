package com.ticatch.concert;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticatch.TestcontainersConfiguration;
import com.ticatch.admin.service.AdminPerformanceService;
import com.ticatch.admin.service.dto.RegisterPerformanceCommand;
import com.ticatch.admin.service.dto.RegisterPerformanceCommand.SeatGradePlan;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.VenueService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 관람객용 공연·좌석 조회. 모두 로그인 없이 부른다(docs/api.md 3절). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ConcertQueryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    VenueService venueService;

    @Autowired
    ConcertService concertService;

    @Autowired
    AdminPerformanceService adminPerformanceService;

    private long venueId;
    private long openConcertId;
    private long onSalePerformanceId;
    private long notYetOpenPerformanceId;

    /** 공개된 공연 하나에 회차 두 개: 지금 예매 중인 회차, 예매 오픈 전인 회차. */
    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        venueId = venueService.create("올림픽홀", "서울 송파구").id();
        openConcertId = concertService.create("겨울 콘서트", "설명").id();
        onSalePerformanceId = register(openConcertId, now.plus(Duration.ofDays(30)), now.minus(Duration.ofDays(1)));
        notYetOpenPerformanceId = register(openConcertId, now.plus(Duration.ofDays(40)), now.plus(Duration.ofDays(10)));
        concertService.open(openConcertId);
    }

    @Test
    void 공개된_공연만_목록에_나온다() throws Exception {
        long preparingConcertId = concertService.create("준비 중인 공연", null).id();

        mockMvc.perform(get("/api/v1/concerts").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].concertId", hasItem((int) openConcertId)))
                .andExpect(jsonPath("$.items[*].concertId", not(hasItem((int) preparingConcertId))))
                .andExpect(jsonPath("$.items[*].status", everyItem(is("OPEN"))));
    }

    @Test
    void 공개되지_않은_공연의_상세는_404_CONCERT_NOT_FOUND다() throws Exception {
        long preparingConcertId = concertService.create("준비 중인 공연", null).id();

        mockMvc.perform(get("/api/v1/concerts/{id}", preparingConcertId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONCERT_NOT_FOUND"));
    }

    @Test
    void 공연_상세는_회차와_지금_예매할_수_있는지를_보여_준다() throws Exception {
        mockMvc.perform(get("/api/v1/concerts/{id}", openConcertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("겨울 콘서트"))
                .andExpect(jsonPath("$.performances[*].performanceId",
                        contains((int) onSalePerformanceId, (int) notYetOpenPerformanceId)))
                .andExpect(jsonPath("$.performances[*].onSale", contains(true, false)))
                .andExpect(jsonPath("$.performances[0].venueName").value("올림픽홀"));
    }

    @Test
    void 등급별_가격과_남은_좌석_수를_준다() throws Exception {
        markSeats(onSalePerformanceId, "VIP-1", "HELD");
        markSeats(onSalePerformanceId, "VIP-2", "SOLD");

        mockMvc.perform(get("/api/v1/performances/{id}/seat-grades", onSalePerformanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onSale").value(true))
                .andExpect(jsonPath("$.seatGrades[*].name", contains("VIP", "R")))
                .andExpect(jsonPath("$.seatGrades[*].price", contains(150000, 100000)))
                .andExpect(jsonPath("$.seatGrades[*].remainingSeats", contains(1, 2)));
    }

    @Test
    void 좌석_배치는_좌석마다_살_수_있는지를_알려_주고_등급으로_거를_수_있다() throws Exception {
        markSeats(onSalePerformanceId, "VIP-1", "HELD");
        long rGradeId = jdbc.queryForObject(
                "SELECT id FROM seat_grade WHERE performance_id = ? AND name = 'R'", Long.class, onSalePerformanceId);

        mockMvc.perform(get("/api/v1/performances/{id}/seats", onSalePerformanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats", hasSize(5)))
                .andExpect(jsonPath("$.seats[*].label", contains("VIP-1", "VIP-2", "VIP-3", "R-1", "R-2")))
                .andExpect(jsonPath("$.seats[*].available", contains(false, true, true, true, true)));

        mockMvc.perform(get("/api/v1/performances/{id}/seats", onSalePerformanceId).param("gradeId", String.valueOf(rGradeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[*].label", contains("R-1", "R-2")));
    }

    @Test
    void 예매_오픈_전인_회차는_좌석은_보이지만_판매_중이_아니다() throws Exception {
        mockMvc.perform(get("/api/v1/performances/{id}/seat-grades", notYetOpenPerformanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onSale").value(false))
                .andExpect(jsonPath("$.seatGrades[*].remainingSeats", contains(3, 2)));
    }

    @Test
    void 공개되지_않은_공연의_회차나_없는_회차는_404_PERFORMANCE_NOT_FOUND다() throws Exception {
        long preparingConcertId = concertService.create("준비 중인 공연", null).id();
        Instant now = Instant.now();
        long hiddenPerformanceId =
                register(preparingConcertId, now.plus(Duration.ofDays(30)), now.minus(Duration.ofDays(1)));

        mockMvc.perform(get("/api/v1/performances/{id}/seat-grades", hiddenPerformanceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/performances/{id}/seats", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
    }

    private long register(long concertId, Instant startsAt, Instant bookingOpensAt) {
        return adminPerformanceService.register(new RegisterPerformanceCommand(
                concertId,
                venueId,
                startsAt,
                bookingOpensAt,
                List.of(new SeatGradePlan("VIP", 150_000, 3), new SeatGradePlan("R", 100_000, 2))))
                .performanceId();
    }

    private void markSeats(long performanceId, String label, String status) {
        jdbc.update("UPDATE seat SET status = ? WHERE performance_id = ? AND label = ?", status, performanceId, label);
    }
}
