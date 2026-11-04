package com.ticatch.user.repository;

import com.ticatch.user.domain.User;
import com.ticatch.user.domain.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndStatus(String email, UserStatus status);

    boolean existsByEmailAndStatus(String email, UserStatus status);
}
