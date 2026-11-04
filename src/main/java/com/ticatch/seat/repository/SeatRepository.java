package com.ticatch.seat.repository;

import com.ticatch.seat.domain.Seat;
import com.ticatch.seat.domain.SeatStatus;
import com.ticatch.seat.repository.projection.SeatGradeStockProjection;
import com.ticatch.seat.repository.projection.SeatProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

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
}
