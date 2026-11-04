package com.ticatch.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 보안 필터에서 생긴 401·403을 {@code GlobalExceptionHandler}로 넘긴다.
 *
 * <p>스프링 시큐리티는 컨트롤러 앞단의 필터에서 동작해서 {@code @RestControllerAdvice}를 거치지 않는다.
 * 예외를 스프링 MVC의 예외 처리기로 넘겨, 다른 에러와 같은 형식(Problem Details + code)으로 응답한다.
 */
@Component
class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    SecurityErrorHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        handlerExceptionResolver.resolveException(request, response, null, exception);
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) {
        handlerExceptionResolver.resolveException(request, response, null, exception);
    }
}
