package com.ticatch.auth.repository;

import com.ticatch.auth.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 토큰을 지우고 지운 행 수를 돌려준다. 같은 토큰으로 동시에 재발급하면 한 요청만 1을 받는다.
     * 나중 요청의 DELETE는 앞 요청이 커밋될 때까지 기다린 뒤, 행이 이미 없어서 0을 받는다.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.id = :id")
    int deleteByIdAndCount(@Param("id") Long id);

    @Modifying
    @Query("delete from RefreshToken t where t.tokenHash = :tokenHash and t.userId = :userId")
    void deleteByTokenHashAndUserId(@Param("tokenHash") String tokenHash, @Param("userId") Long userId);
}
