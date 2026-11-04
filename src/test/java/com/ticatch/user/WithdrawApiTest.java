package com.ticatch.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticatch.TestPerformances;
import com.ticatch.TestTokens;
import com.ticatch.TestcontainersConfiguration;
import com.ticatch.admin.service.AdminPerformanceService;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.VenueService;
import com.ticatch.payment.service.PaymentService;
import com.ticatch.payment.service.dto.PayCommand;
import com.ticatch.point.service.PointService;
import com.ticatch.point.service.dto.ChargePointCommand;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.reservation.service.dto.HoldSeatsCommand;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.SignUpCommand;
import java.util.List;
import java.util.UUID;
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

/** 회원 탈퇴(docs/api.md 3절, docs/requirements.md 5절). 테스트마다 새 회원을 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WithdrawApiTest {

    private static final String PASSWORD = "password1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    UserService userService;

    @Autowired
    PointService pointService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    VenueService venueService;

    @Autowired
    ConcertService concertService;

    @Autowired
    AdminPerformanceService adminPerformanceService;

    @Test
    void 탈퇴하면_204이고_로그인과_재발급이_막히고_같은_이메일로_다시_가입할_수_있다() throws Exception {
        String email = UUID.randomUUID() + "@ticatch.com";
        userService.signUp(new SignUpCommand(email, PASSWORD, "회원"));
        String tokens = login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(tokens, "$.accessToken");

        withdraw(bearer, PASSWORD).andExpect(status().isNoContent());

        login(email)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted((String) JsonPath.read(tokens, "$.refreshToken"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "name": "회원"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void 비밀번호가_틀리면_400_PASSWORD_MISMATCH이고_탈퇴하지_않는다() throws Exception {
        long userId = newUser();

        withdraw(TestTokens.bearer(jwtEncoder, userId, "USER"), "wrong-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtEncoder, userId, "USER")))
                .andExpect(status().isOk());
    }

    @Test
    void 시작하지_않은_회차에_결제_대기나_확정_예매가_있으면_409_ACTIVE_RESERVATION_EXISTS다() throws Exception {
        TestPerformances.Created performance = newPerformance();
        long pendingUser = newUser();
        hold(performance, pendingUser, "VIP-1");
        long confirmedUser = newUser();
        pay(confirmedUser, hold(performance, confirmedUser, "VIP-2"));

        for (long userId : List.of(pendingUser, confirmedUser)) {
            withdraw(TestTokens.bearer(jwtEncoder, userId, "USER"), PASSWORD)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACTIVE_RESERVATION_EXISTS"));
        }
    }

    @Test
    void 이미_시작한_회차의_확정_예매는_탈퇴를_막지_않는다() throws Exception {
        TestPerformances.Created performance = newPerformance();
        long userId = newUser();
        pay(userId, hold(performance, userId, "VIP-1"));
        jdbc.update("UPDATE performance SET starts_at = now() - interval '1 hour' WHERE id = ?",
                performance.performanceId());

        withdraw(TestTokens.bearer(jwtEncoder, userId, "USER"), PASSWORD).andExpect(status().isNoContent());
    }

    private ResultActions withdraw(String bearer, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/me/withdraw")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "%s"}
                        """.formatted(password)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)));
    }

    private long newUser() {
        long userId = userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", PASSWORD, "회원")).id();
        pointService.charge(new ChargePointCommand(userId, 500_000, UUID.randomUUID().toString()));
        return userId;
    }

    private TestPerformances.Created newPerformance() {
        return new TestPerformances(venueService, concertService, adminPerformanceService, jdbc).onSale();
    }

    private long hold(TestPerformances.Created performance, long userId, String label) {
        return reservationService.hold(new HoldSeatsCommand(
                userId, performance.performanceId(), List.of(performance.seat(label)))).summary().id();
    }

    private void pay(long userId, long reservationId) {
        paymentService.pay(new PayCommand(userId, reservationId, UUID.randomUUID().toString()));
    }
}
