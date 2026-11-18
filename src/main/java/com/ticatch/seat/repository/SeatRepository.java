package com.ticatch.seat.repository;

import com.ticatch.seat.domain.Seat;
import com.ticatch.seat.domain.SeatStatus;
import com.ticatch.seat.repository.projection.SeatGradeSalesProjection;
import com.ticatch.seat.repository.projection.SeatGradeStockProjection;
import com.ticatch.seat.repository.projection.SeatProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * 회차의 좌석 중 살 수 있는 좌석만 선점 상태로 바꾸고, 바꾼 행 수를 돌려준다. 읽고 확인하고 바꾸는 일을 문장 하나로 한다.
     * 다른 트랜잭션이 바꾸는 중인 행은 그 트랜잭션이 끝날 때까지 기다린 뒤 조건을 다시 확인한다.
     */
    @Modifying
    @Query("""
            update Seat s set s.status = com.ticatch.seat.domain.SeatStatus.HELD, s.updatedAt = :now
            where s.performanceId = :performanceId and s.id in :seatIds
              and s.status = com.ticatch.seat.domain.SeatStatus.AVAILABLE
            """)
    int holdAvailable(
            @Param("performanceId") Long performanceId, @Param("seatIds") List<Long> seatIds, @Param("now") Instant now);

    long countByPerformanceIdAndIdIn(Long performanceId, List<Long> ids);

    @Query("""
            select new com.ticatch.seat.repository.projection.SeatProjection(s.id, s.label, s.seatGradeId, s.status)
            from Seat s
            where s.performanceId = :performanceId
            order by s.id
            """)
    List<SeatProjection> findSeatMap(@Param("performanceId") Long performanceId);

    @Query("""
            select new com.ticatch.seat.repository.projection.SeatProjection(s.id, s.label, s.seatGradeId, s.status)
            from Seat s
            where s.performanceId = :performanceId and s.seatGradeId = :seatGradeId
            order by s.id
            """)
    List<SeatProjection> findSeatMapByGrade(
            @Param("performanceId") Long performanceId, @Param("seatGradeId") Long seatGradeId);

    @Query("""
            select new com.ticatch.seat.repository.projection.SeatGradeStockProjection(s.seatGradeId, count(s))
            from Seat s
            where s.performanceId = :performanceId and s.status = :status
            group by s.seatGradeId
            """)
    List<SeatGradeStockProjection> countByGrade(
            @Param("performanceId") Long performanceId, @Param("status") SeatStatus status);

    @Query("""
            select new com.ticatch.seat.repository.projection.SeatGradeSalesProjection(
                s.seatGradeId, count(s), sum(case when s.status = :sold then 1 else 0 end))
            from Seat s
            where s.performanceId = :performanceId
            group by s.seatGradeId
            """)
    List<SeatGradeSalesProjection> countSalesByGrade(
            @Param("performanceId") Long performanceId, @Param("sold") SeatStatus sold);
}
