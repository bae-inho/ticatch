package com.ticatch.seat.exception;

import com.ticatch.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SeatErrorCode implements ErrorCode {

    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "이 회차에 없는 좌석이 있습니다."),
    SEAT_NOT_AVAILABLE(HttpStatus.CONFLICT, "이미 선점되었거나 판매된 좌석이 있습니다.");

    private final HttpStatus status;
    private final String message;

    SeatErrorCode(HttpStatus status, String message) {
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
