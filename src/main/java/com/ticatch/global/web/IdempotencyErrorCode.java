package com.ticatch.global.web;

import com.ticatch.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 멱등성 키 에러(docs/api.md 1절). 여러 모듈이 쓰지만 {@code CommonErrorCode}와 나눈다.
 * {@code CommonErrorCode}는 스프링 오류의 HTTP 상태로 코드를 찾는데, 여기에 409가 섞이면 스프링의 409가 이 코드로 잘못 바뀐다.
 */
public enum IdempotencyErrorCode implements ErrorCode {

    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "같은 Idempotency-Key로 다른 요청을 보냈습니다.");

    private final HttpStatus status;
    private final String message;

    IdempotencyErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
