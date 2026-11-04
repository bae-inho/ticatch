package com.ticatch.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
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
import com.ticatch.point.service.PointService;
import com.ticatch.point.service.dto.ChargePointCommand;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.reservation.service.dto.HoldSeatsCommand;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.SignUpCommand;
import java.util.List;
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
 * 결제와 환불(docs/api.md 4절). 실패한 결제가 예매·좌석·잔액을 하나도 바꾸지 않는지 확인하려고
 * {@code @Transactional}을 쓰지 않는다. 테스트마다 새 회원과 새 회차를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentApiTest {

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
    VenueService venueService;

    @Autowired
    ConcertService concertService;

    @Autowired
    AdminPerformanceService adminPerformanceService;

    private TestPerformances performances;
    private TestPerformances.Created performance;
    private long userId;
    private String token;

    @BeforeEach
    void setUp() {
        performances = new TestPerformances(venueService, concertService, adminPerformanceService, jdbc);
        performance = performances.onSale();
        userId = newUser();
        token = TestTokens.bearer(jwtEncoder, userId, "USER");
        pointService.charge(new ChargePointCommand(userId, 500_000, UUID.randomUUID().toString()));
    }

    @Test
    void 결제하면_201이고_예매는_확정되고_좌석은_판매되고_잔액이_줄어든다() throws Exception {
        long reservationId = hold(userId, performance, "VIP-1", "R-1");

        String body = pay(token, reservationId, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount").value(250_000))
                .andExpect(jsonPath("$.pointBalance").value(250_000))
                .andExpect(jsonPath("$.paidAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.refundedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int paymentId = JsonPath.read(body, "$.paymentId");

        mockMvc.perform(get("/api/v1/me/reservations/{id}", reservationId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentId").value(paymentId));
        assertThat(seatStatus("VIP-1")).isEqualTo("SOLD");
        assertThat(seatStatus("R-1")).isEqualTo("SOLD");
        assertThat(pointService.getBalance(userId)).isEqualTo(250_000);
    }

    @Test
    void 같은_멱등성_키로_다시_결제하면_한_번만_결제되고_처음과_같은_응답을_준다() throws Exception {
        long reservationId = hold(userId, performance, "VIP-1");
        String key = UUID.randomUUID().toString();
        String first = pay(token, reservationId, key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        pay(token, reservationId, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value((Integer) JsonPath.read(first, "$.paymentId")))
                .andExpect(jsonPath("$.pointBalance").value(350_000));

        assertThat(pointService.getBalance(userId)).isEqualTo(350_000);
    }

    @Test
    void 같은_멱등성_키로_다른_예매를_결제하면_409_IDEMPOTENCY_KEY_CONFLICT다() throws Exception {
        String key = UUID.randomUUID().toString();
        pay(token, hold(userId, performance, "VIP-1"), key).andExpect(status().isCreated());

        pay(token, hold(userId, performances.onSale(), "VIP-1"), key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void 멱등성_키가_없으면_400_IDEMPOTENCY_KEY_REQUIRED다() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId": %d}
                                """.formatted(hold(userId, performance, "VIP-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void 잔액이_부족하면_409_INSUFFICIENT_POINTS이고_예매와_좌석은_그대로다() throws Exception {
        long poorUserId = newUser();
        long reservationId = hold(poorUserId, performance, "VIP-1");

        pay(TestTokens.bearer(jwtEncoder, poorUserId, "USER"), reservationId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));

        assertThat(reservationStatus(reservationId)).isEqualTo("PENDING");
        assertThat(seatStatus("VIP-1")).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE reservation_id = ?", Integer.class, reservationId))
                .isZero();
    }

    @Test
    void 선점_시간이_지난_예매는_409_RESERVATION_EXPIRED다() throws Exception {
        long reservationId = hold(userId, performance, "VIP-1");
        jdbc.update("UPDATE reservation SET expires_at = now() - interval '1 second' WHERE id = ?", reservationId);

        pay(token, reservationId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
    }

    @Test
    void 결제_대기가_아닌_예매는_409_RESERVATION_NOT_PENDING이고_남의_예매는_404다() throws Exception {
        long reservationId = hold(userId, performance, "VIP-1");
        reservationService.cancel(userId, reservationId);

        pay(token, reservationId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_PENDING"));
        pay(TestTokens.bearer(jwtEncoder, newUser(), "USER"), reservationId, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void 환불하면_잔액이_돌아오고_예매는_취소되고_좌석은_다시_팔_수_있다() throws Exception {
        long reservationId = hold(userId, performance, "VIP-1", "R-1");
        long paymentId = paymentIdOf(pay(token, reservationId, UUID.randomUUID().toString()));

        refund(token, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.pointBalance").value(500_000));

        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELED");
        assertThat(seatStatus("VIP-1")).isEqualTo("AVAILABLE");
        assertThat(pointService.getBalance(userId)).isEqualTo(balanceFromTransactions(userId)).isEqualTo(500_000);
        hold(newUser(), performance, "VIP-1");
    }

    @Test
    void 두_번_환불하면_409_PAYMENT_ALREADY_REFUNDED이고_남의_결제는_404다() throws Exception {
        long paymentId = paymentIdOf(pay(token, hold(userId, performance, "VIP-1"), UUID.randomUUID().toString()));

        refund(TestTokens.bearer(jwtEncoder, newUser(), "USER"), paymentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        refund(token, paymentId).andExpect(status().isOk());
        refund(token, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REFUNDED"));
        assertThat(pointService.getBalance(userId)).isEqualTo(500_000);
    }

    @Test
    void 공연_시작_24시간_전이_지나면_환불할_수_없다() throws Exception {
        long paymentId = paymentIdOf(pay(token, hold(userId, performance, "VIP-1"), UUID.randomUUID().toString()));
        jdbc.update("UPDATE performance SET starts_at = now() + interval '1 hour' WHERE id = ?",
                performance.performanceId());

        refund(token, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_PERIOD_ENDED"));
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id = ?", String.class, paymentId))
                .isEqualTo("PAID");
    }

    private long newUser() {
        return userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", "password1234", "회원")).id();
    }

    private long hold(long holderId, TestPerformances.Created target, String... labels) {
        List<Long> seatIds = java.util.Arrays.stream(labels).map(target::seat).toList();
        return reservationService.hold(new HoldSeatsCommand(holderId, target.performanceId(), seatIds)).summary().id();
    }

    private ResultActions pay(String bearer, long reservationId, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reservationId": %d}
                        """.formatted(reservationId)));
    }

    private ResultActions refund(String bearer, long paymentId) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static long paymentIdOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.paymentId")).longValue();
    }

    private String seatStatus(String label) {
        return jdbc.queryForObject("SELECT status FROM seat WHERE id = ?", String.class, performance.seat(label));
    }

    private String reservationStatus(long reservationId) {
        return jdbc.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId);
    }

    /** 불변식 5: 잔액은 거래 금액의 합과 같다(docs/domain.md 5절). */
    private long balanceFromTransactions(long walletOwnerId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE t.type WHEN 'PAY' THEN -t.amount ELSE t.amount END), 0)
                FROM point_transaction t JOIN point_wallet w ON w.id = t.wallet_id
                WHERE w.user_id = ?
                """, Long.class, walletOwnerId);
    }
}
