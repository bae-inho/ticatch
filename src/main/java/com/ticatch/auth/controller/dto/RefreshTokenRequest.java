package com.ticatch.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** 토큰 재발급과 로그아웃에 쓴다. */
public record RefreshTokenRequest(@NotBlank(message = "refresh token을 입력해 주세요.") String refreshToken) {
}
