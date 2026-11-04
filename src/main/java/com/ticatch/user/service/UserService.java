package com.ticatch.user.service;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.exception.CommonErrorCode;
import com.ticatch.point.service.PointService;
import com.ticatch.reservation.service.ReservationService;
import com.ticatch.user.domain.User;
import com.ticatch.user.domain.UserStatus;
import com.ticatch.user.exception.UserErrorCode;
import com.ticatch.user.repository.UserRepository;
import com.ticatch.user.service.dto.SignUpCommand;
import com.ticatch.user.service.dto.UserResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PointService pointService;
    private final ReservationService reservationService;
    private final Clock clock;

    /** 회원을 만들고 포인트 지갑을 연다. 모든 회원은 지갑을 하나 가진다(docs/domain.md 1절). */
    @Transactional
    public UserResult signUp(SignUpCommand command) {
        if (userRepository.existsByEmailAndStatus(command.email(), UserStatus.ACTIVE)) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_USED);
        }
        User user = User.signUp(command.email(), passwordEncoder.encode(command.password()), command.name());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // 위 확인과 저장 사이에 같은 이메일로 다른 가입 요청이 먼저 저장됐다(uk_users_active_email).
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_USED);
        }
        pointService.openWallet(user.getId());
        return UserResult.from(user);
    }

    /** 이메일과 비밀번호가 맞는 활성 회원을 찾는다. 이메일이 없는지 비밀번호가 틀렸는지는 구분하지 않는다. */
    public Optional<UserResult> findByCredentials(String email, String rawPassword) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .map(UserResult::from);
    }

    public Optional<UserResult> findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(UserResult::from);
    }

    /**
     * 비밀번호를 확인하고 탈퇴시킨다. 아직 시작하지 않은 회차에 결제 대기·확정 예매가 있으면 탈퇴할 수 없다.
     * 탈퇴 뒤에는 로그인과 토큰 재발급이 막히고, 같은 이메일로 다시 가입할 수 있다.
     */
    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.PASSWORD_MISMATCH);
        }
        if (reservationService.hasUpcomingActiveReservation(userId)) {
            throw new BusinessException(UserErrorCode.ACTIVE_RESERVATION_EXISTS);
        }
        user.withdraw(Instant.now(clock));
    }
}
