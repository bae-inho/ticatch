package com.ticatch.seat.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 좌석을 JDBC 배치로 한꺼번에 넣는다. 회차 하나에 좌석이 수만 개까지 생긴다.
 *
 * <p>JPA로 넣지 않는 이유: ID를 DB가 만드는(IDENTITY) 테이블은 Hibernate가 INSERT를 묶어 보내지 못하고 한 행씩 보낸다.
 */
@Repository
@RequiredArgsConstructor
public class SeatBulkRepository {

    private static final int BATCH_SIZE = 1_000;
    private static final String INSERT_SQL = """
            INSERT INTO seat (performance_id, seat_grade_id, label, status, created_at, updated_at)
            VALUES (?, ?, ?, 'AVAILABLE', ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public void insertAll(Long performanceId, List<NewSeat> seats, Instant now) {
        var createdAt = now.atOffset(ZoneOffset.UTC);
        jdbcTemplate.batchUpdate(INSERT_SQL, seats, BATCH_SIZE, (statement, seat) -> {
            statement.setLong(1, performanceId);
            statement.setLong(2, seat.seatGradeId());
            statement.setString(3, seat.label());
            statement.setObject(4, createdAt);
            statement.setObject(5, createdAt);
        });
    }

    public record NewSeat(Long seatGradeId, String label) {
    }
}
