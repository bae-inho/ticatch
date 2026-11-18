package com.ticatch.point.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 환불 거래를 JDBC 배치로 한꺼번에 넣는다. 회차 취소는 결제 수만큼 거래를 만든다.
 * ID를 DB가 만드는(IDENTITY) 테이블이라 Hibernate는 INSERT를 묶어 보내지 못한다(SeatBulkRepository와 같은 이유).
 */
@Repository
@RequiredArgsConstructor
public class PointTransactionBulkRepository {

    private static final int BATCH_SIZE = 1_000;
    private static final String INSERT_SQL = """
            INSERT INTO point_transaction (wallet_id, type, amount, balance_after, payment_id, created_at)
            VALUES (?, 'REFUND', ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public void insertRefunds(List<NewRefund> refunds, Instant now) {
        var createdAt = now.atOffset(ZoneOffset.UTC);
        jdbcTemplate.batchUpdate(INSERT_SQL, refunds, BATCH_SIZE, (statement, refund) -> {
            statement.setLong(1, refund.walletId());
            statement.setLong(2, refund.amount());
            statement.setLong(3, refund.balanceAfter());
            statement.setLong(4, refund.paymentId());
            statement.setObject(5, createdAt);
        });
    }

    public record NewRefund(Long walletId, long amount, long balanceAfter, Long paymentId) {
    }
}
