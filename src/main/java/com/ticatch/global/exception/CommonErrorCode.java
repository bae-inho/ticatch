package com.ticatch.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** 여러 모듈이 함께 쓰는 에러 코드와, 스프링 MVC가 직접 내는 오류에 붙이는 코드. */
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다. Content-Type을 확인해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    CommonErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 스프링 MVC가 낸 오류의 HTTP 상태에 맞는 코드를 고른다.
     * 전용 코드가 없는 4xx는 {@code INVALID_INPUT}, 그 밖은 {@code INTERNAL_ERROR}다.
     */
    static CommonErrorCode from(HttpStatusCode status) {
        for (CommonErrorCode code : values()) {
            if (code.status.value() == status.value()) {
                return code;
            }
        }
        return status.is4xxClientError() ? INVALID_INPUT : INTERNAL_ERROR;
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
