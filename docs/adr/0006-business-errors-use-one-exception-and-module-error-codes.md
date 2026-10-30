# 0006. 업무 에러는 예외 클래스 하나와 모듈별 에러 코드 enum으로 표현한다

- 상태: 채택
- 날짜: 2026-10-30

## 배경

[ADR 0004](0004-error-response-uses-problem-details.md)에서 에러 응답 형식(Problem Details + `code`)을 정했다. 이제 코드 안에서 에러를 어떻게 만들고, 누가 그것을 응답으로 바꿀지 정해야 한다.

이전 버전은 업무 에러를 `IllegalArgumentException`, `IllegalStateException`으로 던지고, 핸들러가 이 두 예외를 모두 `400`과 예외 메시지로 바꿨다. 그 결과 두 가지 문제가 있었다.

1. **업무 에러와 버그를 구분할 수 없었다.** 라이브러리나 우리 코드의 버그로 생긴 `IllegalStateException`도 "사용자의 잘못된 요청"으로 응답됐다. 그때 예외 메시지에 들어 있던 내부 정보가 사용자에게 그대로 나갔다.
2. **에러 종류를 코드로 다룰 수 없었다.** 메시지 문자열이 사실상 유일한 구분자였다.

## 결정

```text
ErrorCode (interface)          code 이름, HTTP 상태, 안내문
 ├─ CommonErrorCode (enum)     INVALID_INPUT, NOT_FOUND, INTERNAL_ERROR ...
 └─ {Module}ErrorCode (enum)   모듈마다 하나. 예: ReservationErrorCode.SEAT_NOT_AVAILABLE

BusinessException              ErrorCode 하나를 들고 있는 예외. 업무 에러는 모두 이것으로 던진다
GlobalExceptionHandler         모든 예외를 Problem Details로 바꾼다
```

- enum 상수 이름이 응답의 `code`다. `ErrorCode`의 `name()`은 enum이 기본으로 가진 메서드라 따로 구현하지 않는다.
- HTTP 상태는 에러 코드가 직접 가진다. 상태를 고르는 기준은 [API 설계](../api.md#400과-409를-가르는-기준)의 400·409 규칙이다.
- `GlobalExceptionHandler`는 스프링의 `ResponseEntityExceptionHandler`를 상속한다. 스프링 MVC가 직접 내는 오류(읽을 수 없는 JSON, 없는 경로, 지원하지 않는 메서드 등)도 같은 형식으로 바꾸기 위해서다.
- `BusinessException`이 아닌 예외는 모두 버그로 보고 `500 INTERNAL_ERROR`로 응답한다. 원인 메시지는 응답에 넣지 않고 ERROR 로그로만 남긴다.

## 검토한 대안

### 상황마다 예외 클래스 (`SeatNotAvailableException` 등)

예외 타입으로 `catch`를 나눌 수 있다. 하지만 이 서비스에서 업무 예외를 타입별로 잡아 다르게 처리할 곳이 없다. 핸들러는 모든 업무 예외를 같은 방식으로 응답으로 바꾸므로, 에러 하나를 추가할 때마다 클래스만 늘어난다.

### 에러 코드는 HTTP 상태를 모르고, 웹 계층이 분류별로 상태를 정함

에러 코드에는 `NOT_FOUND`, `CONFLICT` 같은 분류만 두고, 핸들러가 분류를 HTTP 상태로 바꾼다. 도메인 코드가 스프링 웹(`HttpStatus`)에 의존하지 않는다는 장점이 있다. 하지만 이 프로젝트의 분류는 HTTP 상태와 1:1로 대응해서, 분류 enum이 `HttpStatus`를 이름만 바꿔 다시 만드는 셈이 된다.

### 스프링의 `ErrorResponseException`을 상속

`BusinessException`이 `ProblemDetail`을 직접 들고 있게 하면 핸들러 코드가 줄어든다. 하지만 업무 예외를 만들 때마다 HTTP 응답 객체를 함께 만들게 되어, 업무 코드가 응답 형식에 묶인다.

## 결과

얻는 것:

- 업무 에러(4xx)와 버그(500)가 예외 타입으로 확실히 나뉜다. 버그의 내부 메시지는 응답으로 나가지 않는다.
- 에러 하나를 추가할 때는 모듈 enum에 상수 한 줄만 추가하면 된다.
- 모든 에러 응답 모양을 한 클래스(`GlobalExceptionHandler`)가 정하고, 테스트가 그 계약을 지킨다.

감수하는 것:

- 에러 코드 enum이 `org.springframework.http.HttpStatus`에 의존한다. 업무 모듈이 스프링 웹 라이브러리 하나를 알게 된다.
- 스프링 시큐리티처럼 스프링 MVC 바깥(필터)에서 생기는 오류는 이 핸들러를 거치지 않는다. 5단계(인증)에서 같은 형식으로 응답하도록 따로 맞춘다.
