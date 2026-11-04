package com.ticatch.concert.exception;

import com.ticatch.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ConcertErrorCode implements ErrorCode {

    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
    CONCERT_INVALID_STATUS(HttpStatus.CONFLICT, "지금 공연 상태에서는 할 수 없는 요청입니다."),
    CONCERT_HAS_NO_PERFORMANCE(HttpStatus.CONFLICT, "회차가 하나 이상 있어야 공개할 수 있습니다."),
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연장을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ConcertErrorCode(HttpStatus status, String message) {
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
