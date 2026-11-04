package com.ticatch.auth.service;

import com.ticatch.auth.domain.RefreshToken;
import com.ticatch.auth.exception.AuthErrorCode;
import com.ticatch.auth.repository.RefreshTokenRepository;
import com.ticatch.auth.service.dto.TokenResult;
import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.security.JwtProperties;
import com.ticatch.global.security.SecurityConfig;
import com.ticatch.user.service.UserService;
import com.ticatch.user.service.dto.UserResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인, 토큰 재발급, 로그아웃(ADR 0007). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Transactional
    public TokenResult login(String email, String password) {
        UserResult user = userService.findByCredentials(email, password)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));
        return issueTokens(user);
    }

    /** refresh token 하나로 새 토큰 한 쌍을 받는다. 쓴 refresh token은 지워서 다시 쓸 수 없게 한다(회전). */
    @Transactional
    public TokenResult refresh(String refreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> !token.isExpired(Instant.now(clock)))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        if (refreshTokenRepository.deleteByIdAndCount(saved.getId()) == 0) {
            // 같은 토큰으로 온 다른 요청이 먼저 썼다.
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        UserResult user = userService.findActiveUser(saved.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        return issueTokens(user);
    }

    /** 이 기기의 refresh token을 지운다. 이미 없는 토큰이어도 성공으로 본다. */
    @Transactional
    public void logout(Long userId, String refreshToken) {
        refreshTokenRepository.deleteByTokenHashAndUserId(hash(refreshToken), userId);
    }

    private TokenResult issueTokens(UserResult user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .claim(SecurityConfig.ROLE_CLAIM, user.role().name())
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        String refreshToken = newRefreshToken();
        refreshTokenRepository.save(
                RefreshToken.issue(user.id(), hash(refreshToken), now.plus(jwtProperties.refreshTokenTtl())));
        return new TokenResult(accessToken, jwtProperties.accessTokenTtl().toSeconds(), refreshToken);
    }

    /** 추측할 수 없는 32바이트 난수. 의미 있는 정보를 담지 않는다. */
    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // 모든 자바 런타임은 SHA-256을 지원해야 한다(Java SE 명세).
            throw new IllegalStateException(exception);
        }
    }
}
