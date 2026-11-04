package com.ticatch.concert.repository;

import com.ticatch.concert.domain.Performance;
import com.ticatch.concert.domain.PerformanceStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    boolean existsByConcertIdAndStatus(Long concertId, PerformanceStatus status);

    /** 좌석 등급을 같은 쿼리로 함께 가져온다. 회차마다 등급 쿼리가 따로 나가지 않게 한다(N+1). */
    @EntityGraph(attributePaths = "seatGrades")
    List<Performance> findAllByConcertIdOrderByStartsAt(Long concertId);
}
