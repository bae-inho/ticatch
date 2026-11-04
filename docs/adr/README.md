# 설계 결정 기록 (ADR)

되돌리기 어렵거나 이유를 나중에 다시 물어볼 만한 결정을 남긴다. 한 파일에 결정 하나를 쓴다.

## 작성 규칙

- 파일 이름은 `NNNN-결정-요약.md`이다. 번호는 다시 쓰지 않는다.
- 이미 채택한 ADR은 고치지 않는다. 결정이 바뀌면 새 ADR을 쓰고, 이전 ADR의 상태를 `대체됨(NNNN)`으로 바꾼다.
- 상태는 `제안`, `채택`, `대체됨(NNNN)` 중 하나다.

## 형식

```markdown
# NNNN. 결정 요약

- 상태: 채택
- 날짜: YYYY-MM-DD

## 배경
무엇을 결정해야 했고, 어떤 제약이 있었는가.

## 결정
무엇을 하기로 했는가.

## 검토한 대안
무엇을 고려했고 왜 고르지 않았는가.

## 결과
이 결정으로 얻는 것과 감수하는 것.
```

## 목록

| 번호 | 결정 | 상태 |
| --- | --- | --- |
| [0001](0001-use-postgresql.md) | 데이터베이스로 PostgreSQL을 쓴다 | 채택 |
| [0002](0002-reference-other-aggregates-by-id.md) | 다른 애그리거트는 ID로 참조한다 | 채택 |
| [0003](0003-seat-row-owns-sales-state.md) | 좌석의 판매 상태는 좌석 행이 가진다 | 채택 |
| [0004](0004-error-response-uses-problem-details.md) | 성공 응답은 감싸지 않고, 에러는 Problem Details로 보낸다 | 채택 |
| [0005](0005-store-time-as-timestamptz-and-instant.md) | 시각은 timestamptz와 Instant로 다루고, API에서 한국 시간으로 바꾼다 | 채택 |
| [0006](0006-business-errors-use-one-exception-and-module-error-codes.md) | 업무 에러는 예외 클래스 하나와 모듈별 에러 코드 enum으로 표현한다 | 채택 |
| [0007](0007-authenticate-with-jwt-access-token-and-stored-refresh-token.md) | 인증은 JWT access token과 DB에 저장한 refresh token으로 한다 | 채택 |
