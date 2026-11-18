-- cancel.js용 데이터: 확정 예매 :reservations건(한 건에 1석)으로 매진된 회차 하나(ID 1)를 만든다.
-- reset.sql 바로 뒤에 실행한다. 예매는 회원 2,000명에게 차례로 나눠 주고, 결제와 포인트 차감 기록도
-- 실제 결제와 같은 모양으로 넣는다. 좌석은 등급마다 최대 5,000석이다(회차 등록 API의 한도).

INSERT INTO venue (name, address, created_at, updated_at)
VALUES ('측정홀', '서울', now(), now());

INSERT INTO concert (title, description, status, created_at, updated_at)
VALUES ('측정 공연', NULL, 'OPEN', now(), now());

INSERT INTO performance (concert_id, venue_id, starts_at, booking_opens_at, status, created_at, updated_at)
VALUES (1, 1, now() + interval '30 days', now() - interval '1 day', 'SCHEDULED', now(), now());

INSERT INTO seat_grade (performance_id, name, price, created_at, updated_at)
SELECT 1, 'G' || g, 100000, now(), now()
FROM generate_series(1, ceil(:reservations / 5000.0)::int) AS g;

INSERT INTO seat (performance_id, seat_grade_id, label, status, created_at, updated_at)
SELECT 1, (n - 1) / 5000 + 1, 'G' || ((n - 1) / 5000 + 1) || '-' || ((n - 1) % 5000 + 1), 'SOLD', now(), now()
FROM generate_series(1, :reservations) AS n;

-- 회원 ID는 관리자(1) 다음인 2번부터 2,001번까지다.
INSERT INTO reservation (user_id, performance_id, status, total_amount, expires_at, created_at, updated_at)
SELECT 2 + (n - 1) % 2000, 1, 'CONFIRMED', 100000, now() + interval '10 minutes', now(), now()
FROM generate_series(1, :reservations) AS n;

INSERT INTO reservation_seat (reservation_id, seat_id, price, created_at)
SELECT n, n, 100000, now()
FROM generate_series(1, :reservations) AS n;

INSERT INTO payment (reservation_id, user_id, amount, status, idempotency_key, paid_at, created_at, updated_at)
SELECT r.id, r.user_id, r.total_amount, 'PAID', 'perf-pay-' || r.id, now(), now(), now()
FROM reservation r;

-- 차감 후 잔액은 회원별로 결제 순서대로 줄어든다.
INSERT INTO point_transaction (wallet_id, type, amount, balance_after, payment_id, created_at)
SELECT w.id, 'PAY', p.amount, w.balance - sum(p.amount) OVER (PARTITION BY p.user_id ORDER BY p.id), p.id, now()
FROM payment p
         JOIN point_wallet w ON w.user_id = p.user_id;

UPDATE point_wallet w
SET balance    = w.balance - paid.total,
    updated_at = now()
FROM (SELECT user_id, sum(amount) AS total FROM payment GROUP BY user_id) paid
WHERE w.user_id = paid.user_id;

ANALYZE;
