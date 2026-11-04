# 개발 규칙

코드를 어디에 두고, 무엇에 의존하고, 어떻게 이름 짓고 검증하는지 정한다. 리뷰할 때 이 문서를 기준으로 본다.

## 1. 패키지 구조

최상위 패키지 하나가 [도메인 설계](domain.md#1-모듈과-애그리거트)의 모듈 하나다. 모듈 안은 계층으로 나눈다.

```text
com.ticatch
├── user/            회원, 로그인 계정
├── auth/            로그인, 토큰 발급·재발급, 로그아웃
├── point/           포인트 지갑, 거래
├── concert/         공연, 회차, 좌석 등급, 공연장
├── seat/            좌석과 판매 상태
├── reservation/     선점, 예매
├── payment/         결제, 환불
├── admin/           여러 모듈을 묶는 관리자 기능 (엔티티 없음)
└── global/          설정, 공통 예외, 공통 응답 등 업무와 무관한 기술 코드
```

모듈 안의 구조:

```text
reservation/
├── controller/
│   ├── ReservationController.java
│   └── dto/                   API 요청·응답 (Request, Response)
├── service/
│   ├── ReservationService.java
│   └── dto/                   서비스 입력·결과 (Command, Result)
├── repository/
│   ├── ReservationRepository.java
│   └── projection/            조회 전용 record (Projection)
├── domain/                    엔티티, 상태 enum, 도메인 규칙
└── exception/                 에러 코드 enum (ReservationErrorCode)
```

필요 없는 하위 패키지는 만들지 않는다. 예를 들어 조회 전용 record가 없으면 `projection/`도 없다.

## 2. 의존 규칙

### 계층

```text
controller → service → repository → domain
```

- 화살표 반대 방향으로 import하지 않는다. 특히 서비스는 `controller.dto`를 모른다.
- 컨트롤러는 Request를 서비스 입력으로 바꾸고, 서비스 결과를 Response로 바꾼다.
- 엔티티를 컨트롤러 밖으로 내보내지 않는다. 응답은 항상 Response record다.

### 모듈

- 다른 모듈은 그 모듈의 **서비스**를 통해서만 쓴다. 다른 모듈의 리포지토리와 엔티티를 직접 쓰지 않는다.
- 다른 애그리거트는 ID로 참조한다([ADR 0002](adr/0002-reference-other-aggregates-by-id.md)).
- 모듈 사이 호출은 [도메인 설계의 의존 방향](domain.md#의존-방향)을 따른다. 순환을 만들지 않는다.
- `global`은 업무 모듈에 의존하지 않는다.

이 규칙은 모듈 코드가 생기는 6단계에서 ArchUnit 테스트로 강제한다.

## 3. 이름

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 엔티티 | 도메인 용어 그대로. `Entity` 접미어를 붙이지 않는다 | `Reservation` |
| 상태 enum | `{엔티티}Status` | `ReservationStatus` |
| 요청·응답 | `{동작}{대상}Request`, `{대상}Response` | `HoldSeatsRequest`, `ReservationResponse` |
| 서비스 입력·결과 | `{동작}{대상}Command`, `{대상}Result` | `HoldSeatsCommand`, `ReservationResult` |
| 조회 전용 record | `{대상}Projection` | `SeatGradeStockProjection` |
| 테이블·컬럼 | snake_case, 테이블은 단수 | `reservation_seat`, `booking_opens_at` |

`user`는 PostgreSQL 예약어라 회원 테이블만 `users`다. 용어는 [요구사항의 용어표](requirements.md#3-용어)를 따른다.

## 4. 코드 작성

- 들여쓰기는 공백 4칸, YAML은 2칸이다. [.editorconfig](../.editorconfig)가 IDE에 적용한다.
- DTO는 `record`로 만든다.
- 엔티티에는 `@Setter`와 `@Data`를 쓰지 않는다. 상태는 의미 있는 이름의 메서드(`confirm()`, `cancel()`)로만 바꾼다.
- 엔티티의 기본 생성자는 `protected`다(`@NoArgsConstructor(access = AccessLevel.PROTECTED)`). JPA만 쓰고, 코드에서는 정적 생성 메서드나 생성자로 만든다.
- 시각은 `Instant`로 다루고 "지금"은 주입받은 `Clock`에서 얻는다([ADR 0005](adr/0005-store-time-as-timestamptz-and-instant.md)).
- 엔티티는 `global.jpa.BaseEntity`를 상속해 `created_at`, `updated_at`을 자동으로 채운다. 만든 뒤 바뀌지 않는 행은 `CreatedAtEntity`를 상속한다.
- 로그인한 회원은 컨트롤러에서 `@AuthenticationPrincipal Jwt jwt`로 받고, `Long.valueOf(jwt.getSubject())`가 회원 ID다. 회원 ID를 경로나 본문으로 받지 않는다.

## 5. 예외와 에러 응답

구조와 이유는 [ADR 0006](adr/0006-business-errors-use-one-exception-and-module-error-codes.md)에 있다.

- 업무 규칙 위반은 `throw new BusinessException(ReservationErrorCode.SEAT_NOT_AVAILABLE)`처럼 던진다.
- `IllegalArgumentException`, `IllegalStateException`으로 업무 에러를 표현하지 않는다. 이 예외들은 코드의 버그를 뜻하고, 응답은 `500 INTERNAL_ERROR`가 된다.
- 에러 코드는 모듈의 `exception/{Module}ErrorCode` enum에 둔다. 상수 이름이 응답의 `code`가 된다.
- 새 에러 코드는 [API 설계 6절](api.md#6-에러-코드-목록)에 먼저 추가하고, HTTP 상태는 [400과 409를 가르는 기준](api.md#400과-409를-가르는-기준)을 따른다.
- 검증 애너테이션에는 `message`를 직접 쓴다. 예: `@NotBlank(message = "이름은 비어 있을 수 없습니다.")`. 기본 메시지는 서버의 언어 설정에 따라 바뀐다(한국어 PC에서는 한국어, CI 서버에서는 영어).
- 컨트롤러와 서비스에서 예외를 잡아 응답을 직접 만들지 않는다. 응답은 `GlobalExceptionHandler`가 만든다.
- 로그: 업무 예외는 DEBUG(스택 트레이스 없음), 예상하지 못한 예외는 ERROR(스택 트레이스 포함)로 남는다.

## 6. DB 변경

- 스키마는 Flyway migration이 만든다. 파일은 `src/main/resources/db/migration/V{번호}__{설명}.sql`이다.
- **이미 커밋된 migration 파일은 고치지 않는다.** 바꿀 일이 생기면 다음 번호 파일을 추가한다. Flyway는 적용한 파일의 체크섬을 기록해서, 파일이 바뀌면 기동을 거부한다.
- Hibernate는 `ddl-auto: validate`다. 엔티티와 테이블이 다르면 기동이 실패한다. 엔티티를 바꾸면 migration도 함께 추가한다.
- 도메인 불변식 중 DB로 표현할 수 있는 것은 제약으로 건다([도메인 설계 5절](domain.md#5-불변식과-지키는-곳)).

## 7. 테스트

- DB가 필요한 테스트는 Testcontainers로 띄운 **실제 PostgreSQL**에서 돌린다. H2 같은 대체 DB는 쓰지 않는다. 조건부 유일 인덱스처럼 PostgreSQL에만 있는 기능을 쓰고, DB마다 락과 격리 수준 동작이 달라서 다른 DB로 통과한 테스트는 믿을 수 없다.
- 테스트 메서드 이름은 한국어로 "무엇을 하면 어떻게 된다"를 쓴다. 예: `결제_대기_예매는_회원과_회차마다_하나뿐이다`.
- 버그를 고칠 때는 그 버그를 재현하는 테스트를 먼저 만든다.
- 테스트는 Docker가 실행 중이어야 돈다.

## 8. 커밋

메시지 형식은 `<type>: <한국어 설명>`이다. 설명은 "~한다"로 끝낸다.

| type | 쓰는 때 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작을 바꾸지 않는 구조 변경 |
| `test` | 테스트만 추가·수정 |
| `docs` | 문서만 변경 |
| `build` | 빌드 설정, 의존성 |
| `ci` | CI 설정 |
| `chore` | 그 밖의 잡일 |

한 커밋에는 한 가지 목적의 변경만 담는다.
