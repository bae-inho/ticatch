package com.ticatch.reservation.exception;

import com.ticatch.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReservationErrorCode implements ErrorCode {

    PENDING_RESERVATION_EXISTS(HttpStatus.CONFLICT, "이 회차에 결제를 기다리는 예매가 이미 있습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다."),
    RESERVATION_NOT_PENDING(HttpStatus.CONFLICT, "결제 대기 중인 예매가 아닙니다."),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "선점 시간이 지나 결제할 수 없습니다. 좌석을 다시 골라 주세요."),
    REFUND_PERIOD_ENDED(HttpStatus.CONFLICT, "공연 시작 24시간 전까지만 예매를 취소할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ReservationErrorCode(HttpStatus status, String message) {
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
