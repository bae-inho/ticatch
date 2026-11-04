package com.ticatch.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticatch.TestPerformances;
import com.ticatch.TestcontainersConfiguration;
import com.ticatch.admin.service.AdminPerformanceService;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.concert.service.VenueService;
import com.ticatch.global.exception.BusinessException;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.reservation.service.dto.HoldSeatsCommand;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.SignUpCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 좌석 선점이 동시에 몰릴 때(docs/domain.md 6절 경합 지점 1·2·3).
 * 요청마다 자기 트랜잭션으로 커밋해야 경합이 재현되므로 {@code @Transactional}을 쓰지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservationConcurrencyTest {

    private static final int THREADS = 20;

    @Autowired
    ReservationService reservationService;

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
    void 같은_좌석을_여러_명이_동시에_선점해도_한_명만_성공한다() throws Exception {
        List<Long> users = newUsers(THREADS);
        long seat = performance.seat("VIP-1");

        List<String> outcomes = runConcurrently(i -> new HoldSeatsCommand(users.get(i), performance.performanceId(), List.of(seat)));

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("SEAT_NOT_AVAILABLE"::equals).hasSize(THREADS - 1);
        assertThat(pendingReservations()).isEqualTo(1);
    }

    @Test
    void 두_좌석을_서로_엇갈린_순서로_동시에_선점해도_교착_없이_한_명만_성공한다() throws Exception {
        List<Long> users = newUsers(THREADS);
        long first = performance.seat("VIP-1");
        long second = performance.seat("VIP-2");

        List<String> outcomes = runConcurrently(i -> new HoldSeatsCommand(
                users.get(i), performance.performanceId(), i % 2 == 0 ? List.of(first, second) : List.of(second, first)));

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("SEAT_NOT_AVAILABLE"::equals).hasSize(THREADS - 1);
        assertThat(pendingReservations()).isEqualTo(1);
    }

    @Test
    void 같은_회원이_같은_회차의_다른_좌석을_동시에_선점해도_결제_대기_예매는_하나다() throws Exception {
        long user = newUsers(1).getFirst();
        List<Long> seats = List.copyOf(performance.seatIds().values());

        List<String> outcomes = runConcurrently(seats.size(),
                i -> new HoldSeatsCommand(user, performance.performanceId(), List.of(seats.get(i))));

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("PENDING_RESERVATION_EXISTS"::equals).hasSize(seats.size() - 1);
        assertThat(pendingReservations()).isEqualTo(1);
        Integer heldSeats = jdbc.queryForObject(
                "SELECT count(*) FROM seat WHERE performance_id = ? AND status = 'HELD'",
                Integer.class, performance.performanceId());
        assertThat(heldSeats).isEqualTo(1);
    }

    private List<Long> newUsers(int count) {
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(userService.signUp(new SignUpCommand(UUID.randomUUID() + "@ticatch.com", "password1234", "회원")).id());
        }
        return users;
    }

    private int pendingReservations() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM reservation WHERE performance_id = ? AND status = 'PENDING'",
                Integer.class, performance.performanceId());
    }

    private List<String> runConcurrently(IntFunction<HoldSeatsCommand> command) throws Exception {
        return runConcurrently(THREADS, command);
    }

    /** 스레드를 모두 준비시킨 뒤 한꺼번에 선점을 요청한다. 결과는 성공이면 "OK", 업무 예외면 그 에러 코드다. */
    private List<String> runConcurrently(int threads, IntFunction<HoldSeatsCommand> command) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            HoldSeatsCommand holdCommand = command.apply(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                reservationService.hold(holdCommand);
                return "OK";
            }));
        }
        ready.await();
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
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
