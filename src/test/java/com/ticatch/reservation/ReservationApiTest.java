package com.ticatch.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticatch.TestPerformances;
import com.ticatch.TestTokens;
import com.ticatch.TestcontainersConfiguration;
import com.ticatch.admin.service.AdminPerformanceService;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.VenueService;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.SignUpCommand;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 좌석 선점, 선점 취소, 만료, 내 예매(docs/api.md 3·4절).
 *
 * <p>{@code @Transactional}을 쓰지 않는다. 요청마다 실제처럼 따로 커밋·롤백되어야 "하나라도 실패하면 아무것도 선점하지 않는다"를
 * 확인할 수 있다. 테스트마다 새 회원과 새 회차를 만들어 데이터가 섞이지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReservationApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    UserService userService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    VenueService venueService;

    @Autowired
    ConcertService concertService;

    @Autowired
    AdminPerformanceService adminPerformanceService;

    private TestPerformances performances;
    private TestPerformances.Created performance;
    private String token;

    @BeforeEach
    void setUp() {
        performances = new TestPerformances(venueService, concertService, adminPerformanceService, jdbc);
        performance = performances.onSale();
        token = newUserToken();
    }

    @Test
    void 선점하면_201과_예매를_주고_좌석은_선점_상태가_된다() throws Exception {
        Instant before = Instant.now();

        String body = hold(token, performance.performanceId(), performance.seat("VIP-1"), performance.seat("R-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/me/reservations/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.concertTitle").value("겨울 콘서트"))
                .andExpect(jsonPath("$.totalAmount").value(250_000))
                .andExpect(jsonPath("$.seats[*].label", contains("VIP-1", "R-1")))
                .andExpect(jsonPath("$.seats[*].gradeName", contains("VIP", "R")))
                .andExpect(jsonPath("$.seats[*].price", contains(150_000, 100_000)))
                .andExpect(jsonPath("$.expiresAt", endsWith("+09:00")))
                .andReturn().getResponse().getContentAsString();

        Instant expiresAt = OffsetDateTime.parse(JsonPath.read(body, "$.expiresAt")).toInstant();
        assertThat(expiresAt).isBetween(before.plus(Duration.ofMinutes(10)).minusSeconds(1), Instant.now().plus(Duration.ofMinutes(10)));
        assertThat(seatStatus("VIP-1")).isEqualTo("HELD");
        assertThat(seatStatus("R-1")).isEqualTo("HELD");
    }

    @Test
    void 고른_좌석_중_하나라도_선점돼_있으면_409_SEAT_NOT_AVAILABLE이고_아무것도_선점하지_않는다() throws Exception {
        hold(newUserToken(), performance.performanceId(), performance.seat("R-1")).andExpect(status().isCreated());

        hold(token, performance.performanceId(), performance.seat("VIP-1"), performance.seat("R-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"));

        assertThat(seatStatus("VIP-1")).isEqualTo("AVAILABLE");
    }

    @Test
    void 같은_회차에_결제_대기_예매가_있으면_409_PENDING_RESERVATION_EXISTS다() throws Exception {
        hold(token, performance.performanceId(), performance.seat("VIP-1")).andExpect(status().isCreated());

        hold(token, performance.performanceId(), performance.seat("VIP-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_RESERVATION_EXISTS"));
    }

    @Test
    void 예매_오픈_전인_회차는_409_PERFORMANCE_NOT_ON_SALE이다() throws Exception {
        TestPerformances.Created notYet = performances.notYetOnSale();

        hold(token, notYet.performanceId(), notYet.seat("VIP-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_ON_SALE"));
    }

    @Test
    void 다른_회차의_좌석이면_404_SEAT_NOT_FOUND다() throws Exception {
        TestPerformances.Created other = performances.onSale();

        hold(token, performance.performanceId(), other.seat("VIP-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_FOUND"));
    }

    @Test
    void 좌석이_5개이거나_같은_좌석이_중복되면_400_INVALID_INPUT이다() throws Exception {
        long[] five = performance.seatIds().values().stream().mapToLong(Long::longValue).toArray();
        hold(token, performance.performanceId(), five)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        hold(token, performance.performanceId(), performance.seat("VIP-1"), performance.seat("VIP-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("seatIdsUnique"));
    }

    @Test
    void 선점을_취소하면_좌석을_다시_살_수_있고_두_번_취소할_수는_없다() throws Exception {
        long reservationId = reservationIdOf(hold(token, performance.performanceId(), performance.seat("VIP-1")));

        cancel(newUserToken(), reservationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        cancel(token, reservationId).andExpect(status().isNoContent());
        cancel(token, reservationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_PENDING"));

        assertThat(seatStatus("VIP-1")).isEqualTo("AVAILABLE");
        hold(newUserToken(), performance.performanceId(), performance.seat("VIP-1")).andExpect(status().isCreated());
    }

    @Test
    void 선점_시간이_지난_예매는_만료되고_좌석이_돌아온다() throws Exception {
        long reservationId = reservationIdOf(hold(token, performance.performanceId(), performance.seat("VIP-1")));

        reservationService.expireOverdue(Instant.now().plus(Duration.ofMinutes(11)), 100);

        mockMvc.perform(get("/api/v1/me/reservations/{id}", reservationId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(seatStatus("VIP-1")).isEqualTo("AVAILABLE");
    }

    @Test
    void 시간이_지났지만_아직_만료되지_않은_내_예매가_있으면_바로_만료시키고_새로_선점한다() throws Exception {
        long staleId = reservationIdOf(hold(token, performance.performanceId(), performance.seat("VIP-1")));
        jdbc.update("UPDATE reservation SET expires_at = now() - interval '1 minute' WHERE id = ?", staleId);

        hold(token, performance.performanceId(), performance.seat("VIP-2")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/me/reservations/{id}", staleId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(seatStatus("VIP-1")).isEqualTo("AVAILABLE");
    }

    @Test
    void 내_예매는_최근_예매부터_보이고_상태로_거르며_남의_예매는_보이지_않는다() throws Exception {
        long canceledId = reservationIdOf(hold(token, performance.performanceId(), performance.seat("VIP-1")));
        cancel(token, canceledId).andExpect(status().isNoContent());
        long pendingId = reservationIdOf(hold(token, performance.performanceId(), performance.seat("R-1")));

        mockMvc.perform(get("/api/v1/me/reservations").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].reservationId", contains((int) pendingId, (int) canceledId)))
                .andExpect(jsonPath("$.items[0].concertTitle").value("겨울 콘서트"));
        mockMvc.perform(get("/api/v1/me/reservations").param("status", "PENDING").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.items[*].reservationId", contains((int) pendingId)));
        mockMvc.perform(get("/api/v1/me/reservations/{id}", pendingId).header(HttpHeaders.AUTHORIZATION, newUserToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    private String newUserToken() {
        long userId = userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", "password1234", "회원")).id();
        return TestTokens.bearer(jwtEncoder, userId, "USER");
    }

    private ResultActions hold(String bearer, long performanceId, long... seatIds) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"performanceId": %d, "seatIds": %s}
                        """.formatted(performanceId, Arrays.toString(seatIds))));
    }

    private ResultActions cancel(String bearer, long reservationId) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/{id}/cancel", reservationId)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static long reservationIdOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.reservationId")).longValue();
    }

    private String seatStatus(String label) {
        return jdbc.queryForObject("SELECT status FROM seat WHERE id = ?", String.class, performance.seat(label));
    }
}
