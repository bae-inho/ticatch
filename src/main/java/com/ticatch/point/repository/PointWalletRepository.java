package com.ticatch.point.repository;

import com.ticatch.point.domain.PointWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {

    Optional<PointWallet> findByUserId(Long userId);

    /**
     * 지갑 행을 잠그고 읽는다({@code SELECT ... FOR UPDATE}). 같은 회원의 충전·결제·환불이 동시에 오면
     * 한 요청씩 차례로 잔액을 바꾼다. 잠그지 않으면 두 요청이 같은 잔액을 읽고 각자 더해 한쪽 변경이 사라진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from PointWallet w where w.userId = :userId")
    Optional<PointWallet> findByUserIdForUpdate(@Param("userId") Long userId);
}
