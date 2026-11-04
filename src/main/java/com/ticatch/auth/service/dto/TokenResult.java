package com.ticatch.auth.service.dto;

/**
 * @param accessToken          API 호출에 쓰는 JWT
 * @param accessTokenExpiresIn access token이 유효한 시간(초)
 * @param refreshToken         access token을 다시 받을 때 쓰는 토큰. 한 번만 쓸 수 있다
 */
public record TokenResult(String accessToken, long accessTokenExpiresIn, String refreshToken) {
}
