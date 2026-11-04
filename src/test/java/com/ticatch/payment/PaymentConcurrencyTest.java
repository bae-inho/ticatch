package com.ticatch.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticatch.TestPerformances;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결제가 다른 요청과 동시에 일어날 때(docs/domain.md 6절 경합 지점 4·5·6).
 * 요청마다 자기 트랜잭션으로 커밋해야 경합이 재현되므로 {@code @Transactional}을 쓰지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentConcurrencyTest {

    @Autowired
    PaymentService paymentService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    PointService pointService;

    @Autowired
    UserService userService;

    @Autowired
    VenueService venueService;

    @Autowired
    ConcertService concertService;

    @Autowired
    AdminPerformanceService adminPerformanceService;

    @Autowired
    JdbcTemplate jdbc;

    private TestPerformances.Created performance;

    @BeforeEach
    void setUp() {
        performance = new TestPerformances(venueService, concertService, adminPerformanceService, jdbc).onSale();
    }

    @Test
    void 같은_멱등성_키로_동시에_결제해도_한_번만_결제되고_모두_같은_결과를_받는다() throws Exception {
        long userId = newUserWithPoints(500_000);
        long reservationId = hold(userId, "VIP-1");
        String key = UUID.randomUUID().toString();

        List<Object> outcomes = runConcurrently(10, i -> () -> paymentService.pay(new PayCommand(userId, reservationId, key)).id());

        assertThat(outcomes).hasSize(10).allMatch(Long.class::isInstance);
        assertThat(outcomes.stream().distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE reservation_id = ?", Integer.class, reservationId))
                .isEqualTo(1);
        assertThat(pointService.getBalance(userId)).isEqualTo(350_000);
    }

    @Test
    void 결제와_만료가_동시에_일어나도_예매_좌석_결제_상태가_어긋나지_않는다() throws Exception {
        List<String> labels = List.of("VIP-1", "VIP-2", "VIP-3", "R-1", "R-2");
        List<Long> users = new ArrayList<>();
        List<Long> reservations = new ArrayList<>();
        for (String label : labels) {
            long userId = newUserWithPoints(500_000);
            users.add(userId);
            reservations.add(hold(userId, label));
        }

        // 결제 5건과, 선점 시간이 이미 지났다고 보는 만료 처리 1건을 동시에 출발시킨다.
        Instant afterExpiry = Instant.now().plus(Duration.ofMinutes(11));
        runConcurrently(labels.size() + 1, i -> i < labels.size()
                ? () -> paymentService.pay(new PayCommand(users.get(i), reservations.get(i), UUID.randomUUID().toString()))
                : () -> reservationService.expireOverdue(afterExpiry, 100));

        for (int i = 0; i < labels.size(); i++) {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT r.status AS reservation, s.status AS seat, (SELECT count(*) FROM payment p WHERE p.reservation_id = r.id) AS payments
                    FROM reservation r JOIN reservation_seat rs ON rs.reservation_id = r.id JOIN seat s ON s.id = rs.seat_id
                    WHERE r.id = ?
                    """, reservations.get(i));
            if ("CONFIRMED".equals(row.get("reservation"))) {
                assertThat(row).containsEntry("seat", "SOLD").containsEntry("payments", 1L);
            } else {
                assertThat(row).containsEntry("reservation", "EXPIRED").containsEntry("seat", "AVAILABLE")
                        .containsEntry("payments", 0L);
            }
        }
    }

    @Test
    void 같은_회원의_결제와_충전이_동시에_일어나도_잔액이_맞다() throws Exception {
        long userId = newUserWithPoints(300_000);
        long reservationId = hold(userId, "VIP-1", "R-1");

        runConcurrently(11, i -> i == 0
                ? () -> paymentService.pay(new PayCommand(userId, reservationId, UUID.randomUUID().toString()))
                : () -> pointService.charge(new ChargePointCommand(userId, 1_000, UUID.randomUUID().toString())));

        assertThat(pointService.getBalance(userId)).isEqualTo(300_000 - 250_000 + 10 * 1_000);
    }

    private long newUserWithPoints(long amount) {
        long userId = userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", "password1234", "회원")).id();
        pointService.charge(new ChargePointCommand(userId, amount, UUID.randomUUID().toString()));
        return userId;
    }

    private long hold(long userId, String... labels) {
        List<Long> seatIds = java.util.Arrays.stream(labels).map(performance::seat).toList();
        return reservationService.hold(new HoldSeatsCommand(userId, performance.performanceId(), seatIds)).summary().id();
    }

    /** 작업을 모두 준비시킨 뒤 한꺼번에 출발시킨다. 성공하면 작업의 반환값, 업무 예외면 그 에러 코드를 모은다. */
    private List<Object> runConcurrently(int threads, java.util.function.IntFunction<Callable<?>> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<?> callable = task.apply(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return callable.call();
            }));
        }
        ready.await();
        start.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                outcomes.add(future.get());
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof BusinessException business)) {
                    throw exception;
                }
                outcomes.add(business.getErrorCode().name());
            }
        }
        executor.shutdown();
        return outcomes;
    }
}
