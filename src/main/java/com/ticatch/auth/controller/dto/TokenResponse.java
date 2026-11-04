package com.ticatch.auth.controller.dto;

import com.ticatch.auth.service.dto.TokenResult;

public record TokenResponse(String accessToken, long accessTokenExpiresIn, String refreshToken) {

    public static TokenResponse from(TokenResult tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.accessTokenExpiresIn(), tokens.refreshToken());
    }
}
