package com.ticatch.admin;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 회차 취소가 선점·결제·환불과 동시에 일어날 때(docs/domain.md 6절 경합 지점 8, ADR 0009).
 * 요청마다 자기 트랜잭션으로 커밋해야 경합이 재현되므로 {@code @Transactional}을 쓰지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PerformanceCancelConcurrencyTest {

    private static final long INITIAL_POINTS = 500_000;

    @Autowired
    AdminPerformanceService adminPerformanceService;

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
    JdbcTemplate jdbc;

    @Test
    void 회차_취소와_선점_결제_환불이_동시에_일어나도_교착_없이_모두_취소되고_잔액이_돌아온다() throws Exception {
        TestPerformances.Created performance =
                new TestPerformances(venueService, concertService, adminPerformanceService, jdbc).onSale();
        List<Long> users = new ArrayList<>();
        List<Callable<?>> tasks = new ArrayList<>();
        // 결제를 끝낸 회원 두 명은 환불을, 선점만 한 회원 두 명은 결제를, 한 명은 새로 선점을 시도한다.
        for (String label : List.of("VIP-1", "VIP-2")) {
            long userId = newUserWithPoints(users);
            long paymentId = paymentService.pay(new PayCommand(
                    userId, hold(performance, userId, label), UUID.randomUUID().toString())).id();
            tasks.add(() -> paymentService.refund(userId, paymentId));
        }
        for (String label : List.of("VIP-3", "R-1")) {
            long userId = newUserWithPoints(users);
            long reservationId = hold(performance, userId, label);
            tasks.add(() -> paymentService.pay(new PayCommand(userId, reservationId, UUID.randomUUID().toString())));
        }
        long latecomer = newUserWithPoints(users);
        tasks.add(() -> hold(performance, latecomer, "R-2"));
        tasks.add(() -> adminPerformanceService.cancel(performance.performanceId()));

        List<Object> outcomes = runConcurrently(tasks);

        // 업무 예외가 아닌 예외(교착 상태 감지 등)는 runConcurrently가 그대로 던져 테스트가 실패한다.
        assertThat(outcomes).hasSize(tasks.size());
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id = ?",
                String.class, performance.performanceId())).isEqualTo("CANCELED");
        assertThat(jdbc.queryForList("SELECT status FROM reservation WHERE performance_id = ?",
                String.class, performance.performanceId())).doesNotContain("PENDING", "CONFIRMED");
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM seat WHERE performance_id = ?",
                String.class, performance.performanceId())).containsExactly("AVAILABLE");
        assertThat(jdbc.queryForList("""
                SELECT p.status FROM payment p JOIN reservation r ON r.id = p.reservation_id
                WHERE r.performance_id = ?
                """, String.class, performance.performanceId())).isNotEmpty().containsOnly("REFUNDED");
        for (long userId : users) {
            assertThat(pointService.getBalance(userId)).isEqualTo(balanceFromTransactions(userId)).isEqualTo(INITIAL_POINTS);
        }
    }

    private long newUserWithPoints(List<Long> users) {
        long userId = userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", "password1234", "회원")).id();
        pointService.charge(new ChargePointCommand(userId, INITIAL_POINTS, UUID.randomUUID().toString()));
        users.add(userId);
        return userId;
    }

    private long hold(TestPerformances.Created performance, long userId, String label) {
        return reservationService.hold(new HoldSeatsCommand(
                userId, performance.performanceId(), List.of(performance.seat(label)))).summary().id();
    }

    /** 불변식 5: 잔액은 거래 금액의 합과 같다(docs/domain.md 5절). */
    private long balanceFromTransactions(long walletOwnerId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE t.type WHEN 'PAY' THEN -t.amount ELSE t.amount END), 0)
                FROM point_transaction t JOIN point_wallet w ON w.id = t.wallet_id
                WHERE w.user_id = ?
                """, Long.class, walletOwnerId);
    }

    /** 작업을 모두 준비시킨 뒤 한꺼번에 출발시킨다. 성공하면 작업의 반환값, 업무 예외면 그 에러 코드를 모은다. */
    private static List<Object> runConcurrently(List<Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
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
