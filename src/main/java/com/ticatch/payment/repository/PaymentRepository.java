package com.ticatch.payment.repository;

import com.ticatch.payment.domain.Payment;
import com.ticatch.payment.domain.PaymentStatus;
import com.ticatch.payment.repository.projection.RefundTargetProjection;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * 잠그지 않고 회원 본인 결제의 예매 ID만 읽는다. 환불은 이 예매의 회차를 먼저 잠근 뒤 결제를 잠근다(ADR 0009).
     * 엔티티를 읽지 않아야 뒤의 잠금 조회가 잠근 시점의 상태를 새로 읽는다.
     */
    @Query("select p.reservationId from Payment p where p.id = :id and p.userId = :userId")
    Optional<Long> findReservationIdByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 결제를 잠그고 읽는다. 같은 결제의 환불 요청이 동시에 와도 한 번만 환불된다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockById(Long id);

    /** 회차 취소로 환불할 결제 완료 건을 회원 ID 순서로 읽는다. 엔티티 대신 지갑 환불에 필요한 값만 읽는다. */
    @Query("""
            select new com.ticatch.payment.repository.projection.RefundTargetProjection(p.id, p.userId, p.amount)
            from Payment p
            join Reservation r on r.id = p.reservationId
            where r.performanceId = :performanceId and p.status = com.ticatch.payment.domain.PaymentStatus.PAID
            order by p.userId, p.id
            """)
    List<RefundTargetProjection> findRefundTargets(@Param("performanceId") Long performanceId);

    /** 회차의 결제 완료 건을 한 문장으로 모두 환불 상태로 바꾼다. 회차 취소가 쓴다. */
    @Modifying
    @Query("""
            update Payment p
            set p.status = com.ticatch.payment.domain.PaymentStatus.REFUNDED, p.refundedAt = :now, p.updatedAt = :now
            where p.status = com.ticatch.payment.domain.PaymentStatus.PAID
              and p.reservationId in (select r.id from Reservation r where r.performanceId = :performanceId)
            """)
    int refundAllByPerformanceId(@Param("performanceId") Long performanceId, @Param("now") Instant now);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            join Reservation r on r.id = p.reservationId
            where r.performanceId = :performanceId and p.status = :status
            """)
    long sumAmountByPerformanceIdAndStatus(
            @Param("performanceId") Long performanceId, @Param("status") PaymentStatus status);
}
