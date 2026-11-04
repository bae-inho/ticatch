package com.ticatch.global.web;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.exception.CommonErrorCode;

/** {@code Idempotency-Key} 헤더 값을 확인한다. 결제·충전 컨트롤러에서 쓴다. */
public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";

    /** DB 컬럼 길이(VARCHAR(64))와 같다. 클라이언트는 보통 36자 UUID를 보낸다. */
    private static final int MAX_LENGTH = 64;

    private IdempotencyKeys() {
    }

    public static String require(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (headerValue.length() > MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return headerValue;
    }
}
