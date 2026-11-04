package com.ticatch.global.exception;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러를 RFC 9457 Problem Details 형식에 {@code code}를 더해 응답한다(docs/api.md 2절, ADR 0006).
 *
 * <p>스프링 MVC가 직접 내는 오류(읽을 수 없는 JSON, 없는 경로 등)는 부모 클래스가 잡고,
 * {@link #handleExceptionInternal}에서 우리 형식으로 바꾼다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException exception) {
        // 업무 규칙에 따른 정상적인 실패다. 부하가 몰리면 매우 많이 생기므로 스택 트레이스 없이 DEBUG로 남긴다.
        log.debug("업무 예외: {}", exception.getErrorCode().name());
        return toResponse(exception.getErrorCode());
    }

    /** 토큰이 없거나 올바르지 않을 때. 보안 필터가 {@code SecurityErrorHandler}를 거쳐 넘긴다. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException exception) {
        log.debug("인증 실패: {}", exception.getMessage());
        return toResponse(CommonErrorCode.UNAUTHORIZED);
    }

    /** 로그인했지만 권한이 없을 때. 예: 일반 회원이 관리자 API를 부름. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException exception) {
        log.debug("권한 없음: {}", exception.getMessage());
        return toResponse(CommonErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception) {
        // 원인 메시지에 내부 정보가 있을 수 있어 응답에는 넣지 않고 로그에만 남긴다.
        log.error("예상하지 못한 오류", exception);
        return toResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problemDetail(CommonErrorCode.INVALID_INPUT);
        List<InvalidField> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new InvalidField(
                        error instanceof FieldError fieldError ? fieldError.getField() : null,
                        error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        // 아래 handleExceptionInternal을 거치면 errors가 덮어써지므로 부모 구현을 바로 부른다.
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        CommonErrorCode code = CommonErrorCode.from(statusCode);
        if (code == CommonErrorCode.INTERNAL_ERROR) {
            log.error("요청 처리 중 오류", exception);
        }
        return super.handleExceptionInternal(exception, problemDetail(code), headers, code.status(), request);
    }

    private static ResponseEntity<ProblemDetail> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(problemDetail(code));
    }

    private static ProblemDetail problemDetail(ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.message());
        problem.setProperty("code", code.name());
        return problem;
    }

    /** 입력 검증에 실패한 항목 하나. 객체 전체에 대한 검증이면 {@code field}가 null이다. */
    public record InvalidField(String field, String reason) {
    }
}
