package com.ticatch.payment.exception;

import com.ticatch.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
    PAYMENT_ALREADY_REFUNDED(HttpStatus.CONFLICT, "이미 환불된 결제입니다.");

    private final HttpStatus status;
    private final String message;

    PaymentErrorCode(HttpStatus status, String message) {
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
