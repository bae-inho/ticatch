-- 측정이 끝난 뒤 데이터 정합성을 확인한다. violations가 모두 0이어야 그 측정 결과를 쓴다(docs/domain.md 5절 불변식).

SELECT '1. 한 좌석이 진행 중인 예매 두 개에 들어감' AS rule, count(*) AS violations
FROM (SELECT rs.seat_id
      FROM reservation_seat rs
               JOIN reservation r ON r.id = rs.reservation_id
      WHERE r.status IN ('PENDING', 'CONFIRMED')
      GROUP BY rs.seat_id
      HAVING count(*) > 1) duplicated
UNION ALL
SELECT '10. 좌석 상태와 예매 상태가 다름', count(*)
FROM seat s
         LEFT JOIN (SELECT rs.seat_id, r.status
                    FROM reservation_seat rs
                             JOIN reservation r ON r.id = rs.reservation_id
                    WHERE r.status IN ('PENDING', 'CONFIRMED')) active ON active.seat_id = s.id
WHERE NOT ((s.status = 'AVAILABLE' AND active.status IS NULL)
    OR (s.status = 'HELD' AND active.status = 'PENDING')
    OR (s.status = 'SOLD' AND active.status = 'CONFIRMED'))
UNION ALL
SELECT '6. 확정 예매에 결제 완료가 정확히 하나가 아님', count(*)
FROM reservation r
WHERE r.status = 'CONFIRMED'
  AND (SELECT count(*) FROM payment p WHERE p.reservation_id = r.id AND p.status = 'PAID') <> 1
UNION ALL
SELECT '5. 지갑 잔액과 거래 합계가 다름', count(*)
FROM point_wallet w
WHERE w.balance <> coalesce((SELECT sum(CASE t.type WHEN 'PAY' THEN -t.amount ELSE t.amount END)
                             FROM point_transaction t
                             WHERE t.wallet_id = w.id), 0)
UNION ALL
SELECT '결제 금액과 예매 금액이 다름', count(*)
FROM payment p
         JOIN reservation r ON r.id = p.reservation_id
WHERE p.amount <> r.total_amount
UNION ALL
SELECT '9. 환불된 결제에 환불 거래가 정확히 하나가 아님', count(*)
FROM payment p
WHERE p.status = 'REFUNDED'
  AND (SELECT count(*) FROM point_transaction t WHERE t.payment_id = p.id AND t.type = 'REFUND') <> 1
UNION ALL
SELECT '취소된 예매에 결제 완료가 남음', count(*)
FROM payment p
         JOIN reservation r ON r.id = p.reservation_id
WHERE r.status = 'CANCELED'
  AND p.status = 'PAID';

SELECT performance_id, status, count(*) AS seats
FROM seat
GROUP BY performance_id, status
ORDER BY performance_id, status;
