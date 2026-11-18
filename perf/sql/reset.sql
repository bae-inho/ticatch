-- 성능 측정용 DB(ticatch_perf)를 측정 직전 상태로 되돌린다. 측정할 때마다 같은 데이터에서 시작하기 위해서다.
-- 회원 2,000명(포인트 10,000,000)과 관리자 1명을 넣는다. 비밀번호는 모두 password1234다.

DO
$$
    BEGIN
        IF current_database() <> 'ticatch_perf' THEN
            RAISE EXCEPTION '성능 측정용 DB(ticatch_perf)에서만 실행한다. 지금 DB: %', current_database();
        END IF;
    END
$$;

TRUNCATE users, venue, concert, performance, seat_grade, seat, reservation, reservation_seat,
    payment, point_wallet, point_transaction, refresh_token RESTART IDENTITY CASCADE;

INSERT INTO users (email, password_hash, name, role, status, created_at, updated_at)
VALUES ('perf-admin@ticatch.test', '{bcrypt}$2a$10$ZqD4jFb2sigvY0mNZdy.B.auL1p4EMlYzee3VKpGdtPhDayM6BxaW',
        '관리자', 'ADMIN', 'ACTIVE', now(), now());

INSERT INTO users (email, password_hash, name, role, status, created_at, updated_at)
SELECT 'perf-user-' || n || '@ticatch.test', '{bcrypt}$2a$10$ZqD4jFb2sigvY0mNZdy.B.auL1p4EMlYzee3VKpGdtPhDayM6BxaW',
       '회원' || n, 'USER', 'ACTIVE', now(), now()
FROM generate_series(1, 2000) AS n;

-- 모든 회원은 지갑을 하나 가진다. 회원 지갑에는 충전 거래 하나로 잔액을 넣어, 잔액 = 거래 합계(불변식 5)를 지킨다.
INSERT INTO point_wallet (user_id, balance, created_at, updated_at)
SELECT id, CASE role WHEN 'USER' THEN 10000000 ELSE 0 END, now(), now()
FROM users;

INSERT INTO point_transaction (wallet_id, type, amount, balance_after, idempotency_key, created_at)
SELECT w.id, 'CHARGE', w.balance, w.balance, 'perf-seed-' || w.user_id, now()
FROM point_wallet w
WHERE w.balance > 0;

ANALYZE;
