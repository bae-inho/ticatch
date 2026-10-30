package com.ticatch.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 응답의 code, HTTP 상태, 안내문을 정한다(docs/api.md 6절).
 *
 * <p>모듈마다 이 인터페이스를 구현한 enum을 둔다. enum 상수 이름이 응답의 {@code code}가 된다.
 */
public interface ErrorCode {

    /** 응답의 {@code code}. enum은 상수 이름을 그대로 돌려준다. */
    String name();

    HttpStatus status();

    /** 응답의 {@code detail}. 사용자에게 그대로 보여 줄 수 있는 한국어 문장이다. */
    String message();
}
