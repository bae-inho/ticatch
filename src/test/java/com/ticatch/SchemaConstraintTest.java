package com.ticatch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 불변식 중 DB 제약으로 지키는 것이 실제로 동작하는지 확인한다(docs/domain.md 5절).
 * 애플리케이션 코드가 없어도 DB가 마지막 방어선 역할을 하는지 본다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaConstraintTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 결제_대기_예매는_회원과_회차마다_하나뿐이다() {
        long userId = insertUser("user@ticatch.com", "ACTIVE");
        long performanceId = insertPerformance();
        insertReservation(userId, performanceId, "PENDING");

        assertThatThrownBy(() -> insertReservation(userId, performanceId, "PENDING"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 결제_대기가_아닌_예매는_같은_회차에_여러_개_있을_수_있다() {
        long userId = insertUser("user@ticatch.com", "ACTIVE");
        long performanceId = insertPerformance();

        assertThatCode(() -> {
            insertReservation(userId, performanceId, "CANCELED");
            insertReservation(userId, performanceId, "EXPIRED");
            insertReservation(userId, performanceId, "PENDING");
        }).doesNotThrowAnyException();
    }

    @Test
    void 포인트_잔액은_음수가_될_수_없다() {
        long userId = insertUser("user@ticatch.com", "ACTIVE");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO point_wallet (user_id, balance, created_at, updated_at)
                VALUES (?, -1, now(), now())
                """, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 활성_회원의_이메일은_중복될_수_없지만_탈퇴한_회원의_이메일로는_다시_가입할_수_있다() {
        insertUser("user@ticatch.com", "WITHDRAWN");
        insertUser("user@ticatch.com", "ACTIVE");

        assertThatThrownBy(() -> insertUser("user@ticatch.com", "ACTIVE"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private long insertUser(String email, String status) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, name, role, status, withdrawn_at, created_at, updated_at)
                VALUES (?, 'hash', '회원', 'USER', ?, CASE WHEN ? = 'WITHDRAWN' THEN now() END, now(), now())
                RETURNING id
                """, Long.class, email, status, status);
    }

    private long insertPerformance() {
        long concertId = jdbc.queryForObject("""
                INSERT INTO concert (title, status, created_at, updated_at)
                VALUES ('콘서트', 'OPEN', now(), now())
                RETURNING id
                """, Long.class);
        long venueId = jdbc.queryForObject("""
                INSERT INTO venue (name, address, created_at, updated_at)
                VALUES ('공연장', '서울', now(), now())
                RETURNING id
                """, Long.class);
        return jdbc.queryForObject("""
                INSERT INTO performance (concert_id, venue_id, starts_at, booking_opens_at, status, created_at, updated_at)
                VALUES (?, ?, now() + interval '30 days', now(), 'SCHEDULED', now(), now())
                RETURNING id
                """, Long.class, concertId, venueId);
    }

    private void insertReservation(long userId, long performanceId, String status) {
        jdbc.update("""
                INSERT INTO reservation (user_id, performance_id, status, total_amount, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 10000, now() + interval '10 minutes', now(), now())
                """, userId, performanceId, status);
    }
}
