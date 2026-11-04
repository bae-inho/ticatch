package com.ticatch.reservation.repository;

import com.ticatch.reservation.domain.Reservation;
import com.ticatch.reservation.domain.ReservationStatus;
import com.ticatch.reservation.repository.projection.ReservationSeatProjection;
import com.ticatch.reservation.repository.projection.ReservationSummaryProjection;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 회원 본인의 예매를 잠그고 읽는다. 남의 예매는 찾지 못한다(404). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findByUserIdAndPerformanceIdAndStatus(
            Long userId, Long performanceId, ReservationStatus status);

    /**
     * 만료 시각이 지난 결제 대기 예매를 잠그고 읽는다. 다른 트랜잭션이 잠근 행은 건너뛴다({@code SKIP LOCKED}).
     * 서버 여러 대의 스케줄러가 동시에 돌아도 같은 예매를 두 번 만료시키지 않고, 결제 중인 예매를 기다리지도 않는다.
     */
    @Query(value = """
            SELECT * FROM reservation
            WHERE status = 'PENDING' AND expires_at <= :now
            ORDER BY expires_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Reservation> findOverdueForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            select new com.ticatch.reservation.repository.projection.ReservationSummaryProjection(
                r.id, r.performanceId, c.title, p.startsAt, r.status, r.totalAmount, r.expiresAt, r.createdAt)
            from Reservation r, Performance p, Concert c
            where p.id = r.performanceId and c.id = p.concertId and r.userId = :userId
            """,
            countQuery = "select count(r) from Reservation r where r.userId = :userId")
    Page<ReservationSummaryProjection> findSummaries(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            select new com.ticatch.reservation.repository.projection.ReservationSummaryProjection(
                r.id, r.performanceId, c.title, p.startsAt, r.status, r.totalAmount, r.expiresAt, r.createdAt)
            from Reservation r, Performance p, Concert c
            where p.id = r.performanceId and c.id = p.concertId and r.userId = :userId and r.status = :status
            """,
            countQuery = "select count(r) from Reservation r where r.userId = :userId and r.status = :status")
    Page<ReservationSummaryProjection> findSummariesByStatus(
            @Param("userId") Long userId, @Param("status") ReservationStatus status, Pageable pageable);

    @Query("""
            select new com.ticatch.reservation.repository.projection.ReservationSummaryProjection(
                r.id, r.performanceId, c.title, p.startsAt, r.status, r.totalAmount, r.expiresAt, r.createdAt)
            from Reservation r, Performance p, Concert c
            where p.id = r.performanceId and c.id = p.concertId and r.id = :id and r.userId = :userId
            """)
    Optional<ReservationSummaryProjection> findSummary(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select new com.ticatch.reservation.repository.projection.ReservationSeatProjection(
                rs.seatId, s.label, g.name, rs.price)
            from Reservation r join r.seats rs, Seat s, SeatGrade g
            where r.id = :reservationId and s.id = rs.seatId and g.id = s.seatGradeId
            order by rs.seatId
            """)
    List<ReservationSeatProjection> findSeats(@Param("reservationId") Long reservationId);
}
