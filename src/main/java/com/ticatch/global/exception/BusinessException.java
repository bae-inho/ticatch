package com.ticatch.global.exception;

/**
 * 업무 규칙 때문에 요청을 처리할 수 없을 때 던진다. 예: 이미 팔린 좌석, 잔액 부족.
 *
 * <p>{@link GlobalExceptionHandler}가 에러 코드에 정해진 HTTP 상태와 code로 응답한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
