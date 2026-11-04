package com.ticatch.reservation.scheduler;

import com.ticatch.reservation.service.ReservationService;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 선점 시간이 지난 예매를 주기적으로 만료시키고 좌석을 돌려놓는다.
 *
 * <p>만료의 정확성은 결제할 때 만료 시각을 비교해서 지킨다. 이 작업은 좌석을 다시 팔 수 있게 돌려놓는 역할이라,
 * 주기만큼 늦어도 규칙은 깨지지 않는다(docs/domain.md 7절).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ReservationExpiryScheduler {

    private static final int BATCH_SIZE = 100;

    private final ReservationService reservationService;
    private final Clock clock;

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    void expireOverdueReservations() {
        Instant now = Instant.now(clock);
        int total = 0;
        int expired;
        do {
            // 한 트랜잭션에 너무 많은 행을 잠그지 않도록 나눠서 처리한다.
            expired = reservationService.expireOverdue(now, BATCH_SIZE);
            total += expired;
        } while (expired == BATCH_SIZE);
        if (total > 0) {
            log.info("선점 만료 {}건", total);
        }
    }
}
