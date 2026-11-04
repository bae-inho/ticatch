package com.ticatch.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토큰 설정. 값은 application.yml의 {@code ticatch.jwt}에서 읽는다.
 *
 * @param secret          access token 서명 키. HS256이라 32바이트 이상이어야 한다
 * @param accessTokenTtl  access token 유효 시간
 * @param refreshTokenTtl refresh token 유효 시간
 */
@ConfigurationProperties("ticatch.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
