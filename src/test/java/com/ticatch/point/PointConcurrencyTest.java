package com.ticatch.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticatch.TestcontainersConfiguration;
import com.ticatch.point.service.PointService;
import com.ticatch.point.service.dto.ChargePointCommand;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.SignUpCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 회원의 충전이 동시에 들어올 때(docs/domain.md 6절 경합 지점 5·6).
 *
 * <p>요청마다 자기 트랜잭션으로 커밋해야 동시성이 재현되므로 이 클래스는 {@code @Transactional}을 쓰지 않는다.
 * 데이터는 테스트마다 새 회원으로 나눈다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PointConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired
    PointService pointService;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 같은_회원이_서로_다른_충전을_동시에_해도_잔액이_빠짐없이_늘어난다() throws Exception {
        long userId = newUser();

        runConcurrently(i -> () -> pointService.charge(
                new ChargePointCommand(userId, 1_000, UUID.randomUUID().toString())));

        assertThat(pointService.getBalance(userId)).isEqualTo(THREADS * 1_000L);
        List<Long> balancesAfter = jdbc.queryForList("""
                SELECT t.balance_after FROM point_transaction t JOIN point_wallet w ON w.id = t.wallet_id
                WHERE w.user_id = ? ORDER BY t.id
                """, Long.class, userId);
        assertThat(balancesAfter).containsExactly(1_000L, 2_000L, 3_000L, 4_000L, 5_000L,
                6_000L, 7_000L, 8_000L, 9_000L, 10_000L);
    }

    @Test
    void 같은_멱등성_키의_충전이_동시에_와도_한_번만_충전된다() throws Exception {
        long userId = newUser();
        String key = UUID.randomUUID().toString();

        runConcurrently(i -> () -> pointService.charge(new ChargePointCommand(userId, 5_000, key)));

        assertThat(pointService.getBalance(userId)).isEqualTo(5_000L);
        Integer transactions = jdbc.queryForObject(
                "SELECT count(*) FROM point_transaction WHERE idempotency_key = ?", Integer.class, key);
        assertThat(transactions).isEqualTo(1);
    }

    private long newUser() {
        return userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", "password1234", "회원")).id();
    }

    /** 스레드를 모두 준비시킨 뒤 한꺼번에 출발시킨다. 하나라도 예외가 나면 테스트가 실패한다. */
    private void runConcurrently(IntFunction<Callable<?>> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Callable<?> callable = task.apply(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return callable.call();
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
    }
}
