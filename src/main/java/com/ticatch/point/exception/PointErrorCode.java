package com.ticatch.point.exception;

import com.ticatch.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PointErrorCode implements ErrorCode {

    INSUFFICIENT_POINTS(HttpStatus.CONFLICT, "포인트 잔액이 부족합니다.");

    private final HttpStatus status;
    private final String message;

    PointErrorCode(HttpStatus status, String message) {
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
