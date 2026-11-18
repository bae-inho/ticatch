package com.ticatch.seat.repository;

import com.ticatch.seat.domain.Seat;
import com.ticatch.seat.domain.SeatStatus;
import com.ticatch.seat.repository.projection.SeatGradeSalesProjection;
import com.ticatch.seat.repository.projection.SeatGradeStockProjection;
import com.ticatch.seat.repository.projection.SeatProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * 회차의 좌석들을 잠그지 않고 ID 순서로 읽는다. 바꾼 내용을 저장할 때 버전을 비교해 충돌을 찾는다(낙관적 락).
     * ID 순서로 읽어야 저장할 때도 같은 순서로 행을 바꿔 교착 상태가 생기지 않는다. 다른 회차의 좌석 ID는 결과에 들어오지 않는다.
     */
    @Query("select s from Seat s where s.performanceId = :performanceId and s.id in :seatIds order by s.id")
    List<Seat> findAllInPerformance(@Param("performanceId") Long performanceId, @Param("seatIds") List<Long> seatIds);

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
