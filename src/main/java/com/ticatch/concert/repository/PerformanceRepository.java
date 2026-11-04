package com.ticatch.concert.repository;

import com.ticatch.concert.domain.Performance;
import com.ticatch.concert.domain.PerformanceStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    boolean existsByConcertIdAndStatus(Long concertId, PerformanceStatus status);

    /** 좌석 등급을 같은 쿼리로 함께 가져온다. 회차마다 등급 쿼리가 따로 나가지 않게 한다(N+1). */
    @EntityGraph(attributePaths = "seatGrades")
    List<Performance> findAllByConcertIdOrderByStartsAt(Long concertId);

    /**
     * 회차 행을 공유 잠금으로 읽는다({@code SELECT ... FOR SHARE}). 선점과 환불이 쓴다.
     * 공유 잠금끼리는 서로 막지 않지만, 회차 취소의 배타 잠금과는 서로 기다린다(ADR 0009).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Performance> findWithShareLockById(Long id);

    /** 회차 행을 배타 잠금으로 읽는다({@code SELECT ... FOR UPDATE}). 회차 취소가 쓴다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Performance> findWithLockById(Long id);
}
