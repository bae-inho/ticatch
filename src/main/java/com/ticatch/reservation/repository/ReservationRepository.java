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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 회원 본인의 예매를 잠그고 읽는다. 남의 예매는 찾지 못한다(404). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findWithLockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findByUserIdAndPerformanceIdAndStatus(
            Long userId, Long performanceId, ReservationStatus status);

    /** 잠그지 않고 예매의 회차 ID만 읽는다. 환불이 회차를 먼저 잠그려고 쓴다(ADR 0009). */
    @Query("select r.performanceId from Reservation r where r.id = :id")
    Optional<Long> findPerformanceIdById(@Param("id") Long id);

    /**
     * 회차의 결제 대기·확정 예매를 한 문장으로 모두 취소하고 바꾼 행 수를 돌려준다. 회차 취소가 쓴다.
     * 다른 요청이 바꾸는 중인 예매는 그 요청이 끝날 때까지 기다린 뒤 조건을 다시 확인한다.
     */
    @Modifying
    @Query("""
            update Reservation r set r.status = com.ticatch.reservation.domain.ReservationStatus.CANCELED, r.updatedAt = :now
            where r.performanceId = :performanceId
              and r.status in (com.ticatch.reservation.domain.ReservationStatus.PENDING,
                               com.ticatch.reservation.domain.ReservationStatus.CONFIRMED)
            """)
    int cancelAllByPerformanceId(@Param("performanceId") Long performanceId, @Param("now") Instant now);

    /** 아직 시작하지 않은 회차의 결제 대기·확정 예매가 있는가. 회원 탈퇴 조건이다. */
    @Query("""
            select case when count(r) > 0 then true else false end
            from Reservation r
            join Performance p on p.id = r.performanceId
            where r.userId = :userId and r.status in :statuses and p.startsAt > :now
            """)
    boolean existsUpcoming(
            @Param("userId") Long userId,
            @Param("statuses") List<ReservationStatus> statuses,
            @Param("now") Instant now);

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
                r.id, r.performanceId, c.title, p.startsAt, r.status, r.totalAmount, r.expiresAt, r.createdAt, pay.id)
            from Reservation r
            join Performance p on p.id = r.performanceId
            join Concert c on c.id = p.concertId
            left join Payment pay on pay.reservationId = r.id
            where r.userId = :userId
            """,
            countQuery = "select count(r) from Reservation r where r.userId = :userId")
    Page<ReservationSummaryProjection> findSummaries(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            select new com.ticatch.reservation.repository.projection.ReservationSummaryProjection(
                r.id, r.performanceId, c.title, p.startsAt, r.status, r.totalAmount, r.expiresAt, r.createdAt, pay.id)
            from Reservation r
            join Performance p on p.id = r.performanceId
            join Concert c on c.id = p.concertId
            left join Payment pay on pay.reservationId = r.id
            where r.userId = :userId and r.status = :status
            """,
            countQuery = "select count(r) from Reservation r where r.userId = :userId and r.status = :status")
    Page<ReservationSummaryProjection> findSummariesByStatus(
            @Param("userId") Long userId, @Param("status") ReservationStatus status, Pageable pageable);

    @Query("""
            select new com.ticatch.reservation.repository.projection.ReservationSummaryProjection(
                r.id, r.performanceId, c.title, p.startsAt, r.status, r.totalAmount, r.expiresAt, r.createdAt, pay.id)
            from Reservation r
            join Performance p on p.id = r.performanceId
            join Concert c on c.id = p.concertId
            left join Payment pay on pay.reservationId = r.id
            where r.id = :id and r.userId = :userId
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
