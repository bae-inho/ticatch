package com.ticatch.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticatch.TestPerformances;
import com.ticatch.TestTokens;
import com.ticatch.TestcontainersConfiguration;
import com.ticatch.admin.service.AdminPerformanceService;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.VenueService;
import com.ticatch.global.exception.BusinessException;
import com.ticatch.payment.service.PaymentService;
import com.ticatch.payment.service.dto.PayCommand;
import com.ticatch.point.service.PointService;
import com.ticatch.point.service.dto.ChargePointCommand;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.reservation.service.dto.HoldSeatsCommand;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.SignUpCommand;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 회차 취소와 판매 현황(docs/api.md 4절). 회차 취소가 여러 모듈의 데이터를 함께 바꾸는지 DB에서 확인하려고
 * {@code @Transactional}을 쓰지 않는다. 테스트마다 새 회원과 새 회차를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminPerformanceApiTest {

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

    private TestPerformances.Created performance;
    private String admin;

    @BeforeEach
    void setUp() {
        performance = new TestPerformances(venueService, concertService, adminPerformanceService, jdbc).onSale();
        admin = TestTokens.bearer(jwtEncoder, 1L, "ADMIN");
    }

    @Test
    void 회차를_취소하면_예매를_모두_취소하고_결제를_모두_환불하고_좌석을_돌려놓는다() throws Exception {
        long paidUser = newUserWithPoints(500_000);
        long confirmed = hold(paidUser, "VIP-1", "R-1");
        paymentService.pay(new PayCommand(paidUser, confirmed, UUID.randomUUID().toString()));
        long pendingUser = newUserWithPoints(500_000);
        long pending = hold(pendingUser, "VIP-2");
        long cancelingUser = newUserWithPoints(500_000);
        reservationService.cancel(cancelingUser, hold(cancelingUser, "VIP-3"));

        cancel(performance.performanceId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performanceId").value(performance.performanceId()))
                .andExpect(jsonPath("$.canceledReservations").value(2))
                .andExpect(jsonPath("$.refundedPayments").value(1));

        assertThat(performanceStatus()).isEqualTo("CANCELED");
        assertThat(reservationStatus(confirmed)).isEqualTo("CANCELED");
        assertThat(reservationStatus(pending)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM seat WHERE performance_id = ?",
                String.class, performance.performanceId())).containsExactly("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE reservation_id = ?", String.class, confirmed))
                .isEqualTo("REFUNDED");
        assertThat(pointService.getBalance(paidUser)).isEqualTo(balanceFromTransactions(paidUser)).isEqualTo(500_000);
        assertThat(pointService.getBalance(pendingUser)).isEqualTo(500_000);
    }

    @Test
    void 취소된_회차는_선점할_수_없고_결제는_이미_환불됐고_다시_취소하면_409다() throws Exception {
        long userId = newUserWithPoints(500_000);
        long paymentId = paymentService.pay(
                new PayCommand(userId, hold(userId, "VIP-1"), UUID.randomUUID().toString())).id();
        cancel(performance.performanceId()).andExpect(status().isOk());

        assertThatThrownBy(() -> hold(newUserWithPoints(500_000), "VIP-1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode().name()).isEqualTo("PERFORMANCE_NOT_ON_SALE"));
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtEncoder, userId, "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REFUNDED"));
        cancel(performance.performanceId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_ALREADY_CANCELED"));
        assertThat(pointService.getBalance(userId)).isEqualTo(500_000);
    }

    @Test
    void 판매_현황은_등급별_판매_좌석_수와_환불을_뺀_매출을_준다() throws Exception {
        long buyer = newUserWithPoints(500_000);
        paymentService.pay(new PayCommand(buyer, hold(buyer, "VIP-1", "R-1"), UUID.randomUUID().toString()));
        long refunder = newUserWithPoints(500_000);
        long refundedPayment = paymentService.pay(
                new PayCommand(refunder, hold(refunder, "VIP-2"), UUID.randomUUID().toString())).id();
        paymentService.refund(refunder, refundedPayment);
        hold(newUserWithPoints(500_000), "VIP-3");

        mockMvc.perform(get("/api/v1/admin/performances/{id}/sales", performance.performanceId())
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performanceId").value(performance.performanceId()))
                .andExpect(jsonPath("$.totalSeats").value(5))
                .andExpect(jsonPath("$.soldSeats").value(2))
                .andExpect(jsonPath("$.revenue").value(250_000))
                .andExpect(jsonPath("$.seatGrades[0].name").value("VIP"))
                .andExpect(jsonPath("$.seatGrades[0].price").value(150_000))
                .andExpect(jsonPath("$.seatGrades[0].totalSeats").value(3))
                .andExpect(jsonPath("$.seatGrades[0].soldSeats").value(1))
                .andExpect(jsonPath("$.seatGrades[1].name").value("R"))
                .andExpect(jsonPath("$.seatGrades[1].totalSeats").value(2))
                .andExpect(jsonPath("$.seatGrades[1].soldSeats").value(1));
    }

    @Test
    void 없는_회차는_404_PERFORMANCE_NOT_FOUND다() throws Exception {
        cancel(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/admin/performances/{id}/sales", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
    }

    private ResultActions cancel(long performanceId) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/performances/{id}/cancel", performanceId)
                .header(HttpHeaders.AUTHORIZATION, admin));
    }

    private long newUserWithPoints(long amount) {
        long userId = userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", "password1234", "회원")).id();
        pointService.charge(new ChargePointCommand(userId, amount, UUID.randomUUID().toString()));
        return userId;
    }

    private long hold(long userId, String... labels) {
        List<Long> seatIds = Arrays.stream(labels).map(performance::seat).toList();
        return reservationService.hold(new HoldSeatsCommand(userId, performance.performanceId(), seatIds)).summary().id();
    }

    private String performanceStatus() {
        return jdbc.queryForObject("SELECT status FROM performance WHERE id = ?", String.class, performance.performanceId());
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
