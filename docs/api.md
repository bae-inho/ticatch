# API 설계

[요구사항](requirements.md)과 [도메인 설계](domain.md)를 HTTP API로 옮긴다. 이 문서는 세 가지를 정한다.

1. 모든 API가 따르는 공통 규약
2. 엔드포인트 목록과 핵심 API의 요청·응답
3. 에러 응답 형식과 에러 코드 목록

구현이 시작되면 엔드포인트별 상세 명세는 코드에서 생성한 Swagger(springdoc)가 원본이 된다. 그 뒤에도 이 문서의 공통 규약과 에러 코드 목록은 계속 관리한다.

## 1. 공통 규약

### 경로와 버전

- 모든 API는 `/api/v1`로 시작한다. 호환되지 않게 바뀌면 `/api/v2`를 만든다.
- 리소스 이름은 복수 명사(`/concerts`, `/reservations`)다.
- 로그인한 사용자 자신의 리소스는 `/api/v1/me/...` 아래에 둔다.
- 관리자 API는 `/api/v1/admin/...` 아래에 둔다.

### 인증

- 로그인하면 받는 access token을 `Authorization: Bearer <token>` 헤더로 보낸다.
- **사용자 식별은 토큰으로만 한다.** 경로나 본문으로 `userId`를 받지 않는다. 이전 버전은 `/users/{userId}/...`로 받아 남의 예매를 결제·환불할 수 있었다.
- 토큰은 두 가지다. 자세한 이유는 [ADR 0007](adr/0007-authenticate-with-jwt-access-token-and-stored-refresh-token.md)에 있다.

| 토큰 | 수명 | 쓰는 곳 | 서버 저장 |
| --- | --- | --- | --- |
| access token (JWT) | 30분 | 모든 API의 `Authorization` 헤더 | 저장하지 않는다. 서명과 만료 시각만 검증한다 |
| refresh token | 14일 | `/auth/refresh`로 새 토큰 한 쌍을 받을 때 | 해시를 DB에 저장한다. 한 번 쓰면 폐기한다 |

- 클라이언트는 `401`을 받으면 refresh token으로 한 번만 재발급을 시도한다. 재발급도 `401`이면 다시 로그인하게 한다. 만료와 위조를 다른 코드로 나누지 않으므로, 클라이언트는 이유와 관계없이 이 순서를 따른다.
- 로그아웃은 refresh token을 폐기한다. 이미 발급한 access token은 남은 수명(최대 30분) 동안 유효하다.
- 토큰 없이 부를 수 있는 API는 회원가입, 로그인, 토큰 재발급, 공연 조회(`GET /concerts/**`, `GET /performances/**`), 상태 확인(`/actuator/health`)뿐이다.

### 요청·응답 형식

| 항목 | 규칙 | 예 |
| --- | --- | --- |
| 본문 | JSON, 필드 이름은 camelCase | `"bookingOpensAt"` |
| 시각 | ISO-8601, 한국 시간 오프셋 포함 | `"2026-12-24T19:00:00+09:00"` |
| 금액·포인트 | 정수(원 단위) | `150000` |
| ID | 숫자 | `55` |
| 상태 값 | 대문자 enum 이름 | `"PENDING"` |
| 빈 값 | 값이 없는 필드는 `null`로 보낸다(필드를 빼지 않는다) | `"refundedAt": null` |

### 성공 응답

성공 응답은 **결과 자체를 본문으로 보낸다.** `{ "success": true, "data": ... }` 같은 공통 봉투로 감싸지 않는다. 이유는 [ADR 0004](adr/0004-error-response-uses-problem-details.md)에 있다.

| 상태 | 언제 |
| --- | --- |
| `200 OK` | 조회, 본문을 돌려주는 상태 변경 |
| `201 Created` | 새 리소스 생성. 그 리소스를 조회하는 API가 있으면 `Location` 헤더에 주소를 넣는다 |
| `204 No Content` | 돌려줄 본문이 없는 성공 |

### 상태를 바꾸는 API

필드를 고치는 것이 아니라 규칙이 있는 상태 전이(공개, 취소, 환불)는 `POST /{리소스}/{id}/{동작}`으로 표현한다. 예: `POST /api/v1/admin/concerts/3/open`.

`PATCH { "status": "OPEN" }`처럼 상태 값을 직접 받으면, 서버가 허용하지 않는 전이(`ENDED` → `PREPARING` 등)를 일일이 막아야 하고 요청만 보고는 어떤 업무 동작인지 알기 어렵다.

### 목록 응답

목록은 페이지 단위로 준다. 요청은 `?page=0&size=20`이다.

- `page`는 0부터 시작한다. 기본값은 0이다.
- `size`의 기본값은 20, 최댓값은 100이다. 범위를 벗어나면 `400 INVALID_INPUT`이다.
- 정렬은 클라이언트가 고르지 않는다. API마다 서버가 정한다(예: 최근 등록순).

```json
{
  "items": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "totalPages": 7
}
```

Spring의 `Page` 객체를 그대로 응답하지 않는다. `Page`의 JSON 모양은 Spring 버전에 따라 바뀔 수 있어서, 우리가 정한 형태로 옮겨 담는다.

### 멱등성 (중복 요청 방지)

돈이 오가는 요청(결제, 포인트 충전)은 `Idempotency-Key` 헤더를 반드시 받는다. 클라이언트가 요청마다 새 UUID를 만들어 보내고, 네트워크 오류로 다시 보낼 때는 같은 키를 쓴다.

| 상황 | 서버 동작 |
| --- | --- |
| 처음 보는 키 | 정상 처리 |
| 같은 키, 같은 요청 | 다시 처리하지 않고 처음 결과를 같은 상태 코드로 돌려준다 |
| 같은 키, 다른 요청 | `409 IDEMPOTENCY_KEY_CONFLICT` |
| 키 없음 | `400 IDEMPOTENCY_KEY_REQUIRED` |
| 키가 64자 초과 | `400 INVALID_INPUT` |

"같은 요청"은 결제면 같은 `reservationId`, 충전이면 같은 회원의 같은 `amount`를 뜻한다. 같은 키의 요청이 동시에 와도 한 번만 처리하고, 나머지는 처음 결과를 받는다.

## 2. 에러 응답

### 형식

에러는 [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 형식에 `code` 필드를 더해 보낸다. `Content-Type`은 `application/problem+json`이다. Spring Boot가 이 형식(`ProblemDetail`)을 기본으로 지원한다.

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "이미 선점되었거나 판매된 좌석이 있습니다.",
  "instance": "/api/v1/reservations",
  "code": "SEAT_NOT_AVAILABLE"
}
```

| 필드 | 뜻 |
| --- | --- |
| `status` | HTTP 상태 코드. 응답 헤더의 상태와 항상 같다 |
| `title` | 상태 코드의 이름 |
| `detail` | 사용자에게 보여 줄 수 있는 한국어 설명 |
| `instance` | 요청 경로 |
| `code` | 에러 종류를 나타내는 고정 문자열. 클라이언트는 이 값으로 분기한다 |

표준 필드 `type`은 보내지 않는다. RFC 9457에서 `type`이 없으면 `about:blank`, 즉 "HTTP 상태 코드 외에 따로 정의한 문제 유형이 없다"는 뜻이다. 에러 종류는 `code`로 구분한다.

입력 검증 실패(`INVALID_INPUT`)는 어떤 필드가 왜 틀렸는지 `errors`로 함께 준다.

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "입력값이 올바르지 않습니다.",
  "instance": "/api/v1/reservations",
  "code": "INVALID_INPUT",
  "errors": [
    { "field": "seatIds", "reason": "1개 이상 4개 이하로 선택해야 합니다." }
  ]
}
```

### 규칙

- **성공이 아니면 2xx를 쓰지 않는다.** 이전 버전은 일부 에러를 `200 OK`에 에러 본문을 담아 보냈다.
- **`code`는 계약이고 `detail`은 안내문이다.** `code`는 한 번 정하면 바꾸지 않는다. `detail` 문구는 바꿀 수 있으므로 클라이언트가 문구로 분기하면 안 된다.
- **내부 정보를 노출하지 않는다.** 예외 클래스 이름, 스택 트레이스, SQL은 응답에 넣지 않고 서버 로그에만 남긴다.
- **남의 리소스는 없는 것처럼 답한다.** 남의 예매를 조회하거나 결제하면 `403`이 아니라 `404`를 준다. `403`을 주면 그 ID의 예매가 존재한다는 사실이 드러난다.
- **공개되지 않은 리소스도 없는 것처럼 답한다.** 준비 중인 공연을 관람객이 조회하면 `404`다.

### 400과 409를 가르는 기준

> 같은 요청을 언제 다시 보내도 실패하면 `400`, 서버 상태가 바뀌면 성공할 수 있으면 `409`다.

| 예 | 상태 | 이유 |
| --- | --- | --- |
| 좌석을 5개 고름 | `400` | 언제 보내도 규칙 위반이다 |
| 이미 팔린 좌석을 고름 | `409` | 좌석이 취소되면 성공할 수 있다 |
| 잔액이 부족함 | `409` | 충전하면 성공한다 |
| 예매 오픈 전에 선점 | `409` | 오픈 시각이 지나면 성공한다 |

## 3. 엔드포인트

`인증` 열: `-` 필요 없음, `USER` 로그인 필요, `ADMIN` 관리자만.

### 회원·인증

| 메서드 | 경로 | 설명 | 인증 | 성공 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/users` | 회원가입 | - | 201 |
| POST | `/api/v1/auth/login` | 로그인 | - | 200 |
| POST | `/api/v1/auth/refresh` | 토큰 재발급 | - | 200 |
| POST | `/api/v1/auth/logout` | 로그아웃 | USER | 204 |
| GET | `/api/v1/me` | 내 정보 | USER | 200 |
| POST | `/api/v1/me/withdraw` | 회원 탈퇴 (비밀번호 확인) | USER | 204 |

탈퇴는 비밀번호를 본문으로 받아야 해서 `DELETE` 대신 `POST`를 쓴다. `DELETE` 요청의 본문은 일부 서버·프록시가 무시한다. 관리자 계정은 API로 만들지 않고 초기 데이터로 넣는다.

### 포인트

| 메서드 | 경로 | 설명 | 인증 | 성공 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/me/points` | 잔액 조회 | USER | 200 |
| POST | `/api/v1/me/points/charges` | 충전 (`Idempotency-Key` 필수) | USER | 201 |
| GET | `/api/v1/me/points/transactions` | 거래 내역 (목록) | USER | 200 |

### 공연 조회

| 메서드 | 경로 | 설명 | 인증 | 성공 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/concerts` | 공개된 공연 목록 (목록) | - | 200 |
| GET | `/api/v1/concerts/{concertId}` | 공연 상세와 회차 목록 | - | 200 |
| GET | `/api/v1/performances/{performanceId}/seat-grades` | 등급별 가격과 잔여 좌석 수 | - | 200 |
| GET | `/api/v1/performances/{performanceId}/seats` | 좌석 배치 (`?gradeId=`로 등급 필터) | - | 200 |

좌석 배치는 좌석마다 `available`(살 수 있는가)만 알려 준다. 선점 중인지 판매됐는지는 구분하지 않는다. 관람객에게 필요 없는 정보이고, 선점 중인 좌석을 노려 새로고침하는 행동을 부추기지 않기 위해서다.

공개되지 않은 공연과 그 회차는 `404`다(`CONCERT_NOT_FOUND`, `PERFORMANCE_NOT_FOUND`). 회차 응답의 `onSale`은 "지금 예매할 수 있는가"이고, 공연이 공개돼 있고 회차가 취소되지 않았으며 지금이 예매 오픈 시각 이후·공연 시작 전일 때 `true`다. 예매 오픈 전에도 좌석과 가격은 볼 수 있다.

### 예매

| 메서드 | 경로 | 설명 | 인증 | 성공 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/reservations` | 좌석 선점 (`PENDING` 예매 생성) | USER | 201 |
| POST | `/api/v1/reservations/{reservationId}/cancel` | 선점 취소 (`PENDING`만) | USER | 204 |
| GET | `/api/v1/me/reservations` | 내 예매 목록 (`?status=`로 필터, 목록) | USER | 200 |
| GET | `/api/v1/me/reservations/{reservationId}` | 내 예매 상세 | USER | 200 |

### 결제·환불

| 메서드 | 경로 | 설명 | 인증 | 성공 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/payments` | 결제 (`Idempotency-Key` 필수) | USER | 201 |
| POST | `/api/v1/payments/{paymentId}/refund` | 예매 취소와 환불 | USER | 200 |

선점 취소와 예매 취소를 다른 API로 나눴다. 결제 전 취소는 좌석만 풀면 되는 예매 모듈의 일이고, 결제 후 취소는 돈을 돌려주는 결제 모듈의 일이다([도메인 설계 1절](domain.md#의존-방향)에서 `payment`가 `reservation`을 호출하고, 반대 방향은 없다). 한 API로 합치면 예매 모듈이 결제 모듈을 호출해야 해서 의존 방향이 뒤집힌다.

### 관리자

| 메서드 | 경로 | 설명 | 성공 |
| --- | --- | --- | --- |
| POST | `/api/v1/admin/venues` | 공연장 등록 | 201 |
| GET | `/api/v1/admin/venues` | 공연장 목록 (목록) | 200 |
| POST | `/api/v1/admin/concerts` | 공연 등록 (`PREPARING`) | 201 |
| GET | `/api/v1/admin/concerts` | 공연 목록 (`?status=`로 필터, 목록) | 200 |
| GET | `/api/v1/admin/concerts/{concertId}` | 공연 상세 (모든 상태, 회차 포함) | 200 |
| PATCH | `/api/v1/admin/concerts/{concertId}` | 제목·설명 수정 (두 필드를 모두 보낸다) | 200 |
| POST | `/api/v1/admin/concerts/{concertId}/open` | 공개 | 204 |
| POST | `/api/v1/admin/concerts/{concertId}/end` | 종료 | 204 |
| POST | `/api/v1/admin/concerts/{concertId}/performances` | 회차 등록 (좌석 일괄 생성) | 201 |
| POST | `/api/v1/admin/performances/{performanceId}/cancel` | 회차 취소와 일괄 환불 | 200 |
| GET | `/api/v1/admin/performances/{performanceId}/sales` | 판매 현황 | 200 |

모든 관리자 API는 `ADMIN` 권한이 필요하다.

## 4. 핵심 API 상세

### 로그인 — `POST /api/v1/auth/login`

```json
{
  "email": "user@ticatch.com",
  "password": "password1234"
}
```

`200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 1800,
  "refreshToken": "q3Jx0b9w4pQ..."
}
```

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 이메일이나 비밀번호가 비어 있음 |
| 401 | `LOGIN_FAILED` | 가입하지 않은 이메일이거나 비밀번호가 틀림 (어느 쪽인지 알려 주지 않는다) |

### 토큰 재발급 — `POST /api/v1/auth/refresh`

```json
{
  "refreshToken": "q3Jx0b9w4pQ..."
}
```

`200 OK`. 응답은 로그인과 같다. 보낸 refresh token은 폐기되므로 응답의 새 refresh token을 저장해야 한다.

| 상태 | code | 상황 |
| --- | --- | --- |
| 401 | `INVALID_REFRESH_TOKEN` | 없거나, 만료됐거나, 이미 쓴 refresh token |

### 로그아웃 — `POST /api/v1/auth/logout`

access token이 필요하다. 본문은 재발급과 같다(`refreshToken`). `204 No Content`. 이미 폐기된 토큰이어도 `204`다.

### 등급별 잔여 좌석 — `GET /api/v1/performances/{performanceId}/seat-grades`

`200 OK`

```json
{
  "performanceId": 10,
  "onSale": true,
  "seatGrades": [
    { "seatGradeId": 21, "name": "VIP", "price": 150000, "remainingSeats": 37 },
    { "seatGradeId": 22, "name": "R", "price": 120000, "remainingSeats": 0 }
  ]
}
```

### 좌석 배치 — `GET /api/v1/performances/{performanceId}/seats?gradeId=21`

`gradeId`를 빼면 모든 등급의 좌석을 준다. `200 OK`

```json
{
  "performanceId": 10,
  "onSale": true,
  "seats": [
    { "seatId": 101, "label": "VIP-1", "seatGradeId": 21, "available": false },
    { "seatId": 102, "label": "VIP-2", "seatGradeId": 21, "available": true }
  ]
}
```

### 좌석 선점 — `POST /api/v1/reservations`

```json
{
  "performanceId": 10,
  "seatIds": [101, 102]
}
```

`201 Created`, `Location: /api/v1/me/reservations/55`

```json
{
  "reservationId": 55,
  "performanceId": 10,
  "concertTitle": "겨울 콘서트",
  "startsAt": "2026-12-24T19:00:00+09:00",
  "status": "PENDING",
  "totalAmount": 300000,
  "expiresAt": "2026-12-01T20:10:00+09:00",
  "createdAt": "2026-12-01T20:00:00+09:00",
  "seats": [
    { "seatId": 101, "label": "VIP-1", "gradeName": "VIP", "price": 150000 },
    { "seatId": 102, "label": "VIP-2", "gradeName": "VIP", "price": 150000 }
  ]
}
```

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 좌석이 0개이거나 5개 이상, 같은 좌석 ID가 중복됨 |
| 404 | `PERFORMANCE_NOT_FOUND` | 회차가 없음 |
| 404 | `SEAT_NOT_FOUND` | 그 회차의 좌석이 아닌 ID가 섞여 있음 |
| 409 | `PERFORMANCE_NOT_ON_SALE` | 판매 기간이 아니거나, 회차가 취소됐거나, 공연이 공개 상태가 아님 |
| 409 | `PENDING_RESERVATION_EXISTS` | 이 회차에 이미 결제 대기 중인 예매가 있음 |
| 409 | `SEAT_NOT_AVAILABLE` | 고른 좌석 중 하나라도 선점·판매됨 (전부 실패) |

- 내 예매 상세(`GET /api/v1/me/reservations/{id}`)도 위 응답과 같은 모양이다. 내 예매 목록은 `seats`를 뺀 모양을 최근 예매부터 준다.
- 결제 대기 예매가 선점 시간(10분)을 넘기면 서버가 30초마다 `EXPIRED`로 바꾸고 좌석을 돌려놓는다. 이미 시간이 지난 자기 예매가 있는 상태에서 같은 회차를 다시 선점하면, 그 예매를 바로 만료시키고 새로 선점한다.

### 선점 취소 — `POST /api/v1/reservations/{reservationId}/cancel`

본문 없음. `204 No Content`. 좌석은 바로 다시 살 수 있게 된다.

| 상태 | code | 상황 |
| --- | --- | --- |
| 404 | `RESERVATION_NOT_FOUND` | 예매가 없거나 본인 예매가 아님 |
| 409 | `RESERVATION_NOT_PENDING` | 결제 대기 상태가 아님 (이미 취소·만료·결제됨) |

### 결제 — `POST /api/v1/payments`

헤더 `Idempotency-Key: 0b8f1c2e-5a7d-4e3b-9c61-2f4d8a1b7e90`

```json
{
  "reservationId": 55
}
```

`201 Created`

```json
{
  "paymentId": 77,
  "reservationId": 55,
  "amount": 300000,
  "status": "PAID",
  "paidAt": "2026-12-01T20:05:12+09:00",
  "pointBalance": 700000
}
```

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | `Idempotency-Key` 헤더가 없음 |
| 404 | `RESERVATION_NOT_FOUND` | 예매가 없거나 본인 예매가 아님 |
| 409 | `RESERVATION_NOT_PENDING` | 이미 결제됐거나 취소·만료됨 |
| 409 | `RESERVATION_EXPIRED` | 선점 시간(10분)이 지남 |
| 409 | `INSUFFICIENT_POINTS` | 잔액 부족 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 같은 키로 다른 예매를 결제하려 함 |

### 예매 취소·환불 — `POST /api/v1/payments/{paymentId}/refund`

본문 없음. `200 OK`

```json
{
  "paymentId": 77,
  "reservationId": 55,
  "amount": 300000,
  "status": "REFUNDED",
  "refundedAt": "2026-12-02T10:00:00+09:00",
  "pointBalance": 1000000
}
```

| 상태 | code | 상황 |
| --- | --- | --- |
| 404 | `PAYMENT_NOT_FOUND` | 결제가 없거나 본인 결제가 아님 |
| 409 | `PAYMENT_ALREADY_REFUNDED` | 이미 환불됨 |
| 409 | `REFUND_PERIOD_ENDED` | 공연 시작 24시간 전이 지남 |

### 포인트 충전 — `POST /api/v1/me/points/charges`

헤더 `Idempotency-Key` 필수.

```json
{
  "amount": 50000
}
```

`201 Created`

```json
{
  "transactionId": 301,
  "type": "CHARGE",
  "amount": 50000,
  "balanceAfter": 1050000,
  "createdAt": "2026-12-01T19:58:00+09:00"
}
```

잔액 조회(`GET /api/v1/me/points`)는 `{"balance": 1050000}`이고, 거래 내역(`GET /api/v1/me/points/transactions`)은 위 거래 모양을 최근 거래부터 목록으로 준다.

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 금액이 1 미만이거나 1,000,000 초과 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 헤더가 없음 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 같은 키로 다른 금액을 충전하려 함 |

### 회차 등록 — `POST /api/v1/admin/concerts/{concertId}/performances`

```json
{
  "venueId": 1,
  "startsAt": "2026-12-24T19:00:00+09:00",
  "bookingOpensAt": "2026-12-01T20:00:00+09:00",
  "seatGrades": [
    { "name": "VIP", "price": 150000, "seatCount": 100 },
    { "name": "R", "price": 120000, "seatCount": 300 }
  ]
}
```

좌석 이름은 `{등급 이름}-{번호}`로 만든다(`VIP-1` … `VIP-100`).

`201 Created`

```json
{
  "performanceId": 10,
  "totalSeats": 400
}
```

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 예매 오픈이 공연 시작 이후, 공연 시작이 과거, 등급 이름 중복, 등급 0개 또는 11개 이상, 등급별 좌석 수가 1 미만이거나 5,000 초과, 가격이 1 미만 |
| 404 | `CONCERT_NOT_FOUND` | 공연이 없음 |
| 404 | `VENUE_NOT_FOUND` | 공연장이 없음 |
| 409 | `CONCERT_INVALID_STATUS` | 종료된 공연 |

### 회차 취소 — `POST /api/v1/admin/performances/{performanceId}/cancel`

본문 없음. `200 OK`

```json
{
  "performanceId": 10,
  "canceledReservations": 52,
  "refundedPayments": 48
}
```

| 상태 | code | 상황 |
| --- | --- | --- |
| 404 | `PERFORMANCE_NOT_FOUND` | 회차가 없음 |
| 409 | `PERFORMANCE_ALREADY_CANCELED` | 이미 취소된 회차 |

예매가 많은 회차는 한 번에 처리하기 어려울 수 있다([도메인 설계 7절](domain.md#7-받아들인-한계)). 6단계에서 나눠서 처리하기로 하면 응답을 `202 Accepted`로 바꾸고 이 문서를 고친다.

## 5. 입력 검증 규칙

이번 단계에서 새로 정한 값이다. 요구사항에 없던 값이라 검토가 필요하다.

| 대상 | 규칙 |
| --- | --- |
| 이메일 | 이메일 형식, 최대 100자 |
| 비밀번호 | 8자 이상 64자 이하 |
| 이름 | 1자 이상 20자 이하 |
| 공연 제목 | 1자 이상 100자 이하 |
| 공연 설명 | 최대 2,000자 |
| 공연장 이름·주소 | 1자 이상 100자 이하, 1자 이상 200자 이하 |
| 좌석 등급 | 회차당 1~10개, 이름 1자 이상 10자 이하, 회차 안에서 중복 불가 |
| 등급별 좌석 수 | 1 이상 5,000 이하 |
| 가격 | 1 이상 10,000,000 이하 |
| 충전 금액 | 1 이상 1,000,000 이하 |
| 선점 좌석 수 | 1 이상 4 이하, 중복 불가 |

## 6. 에러 코드 목록

`code`는 `대상_상황` 형태의 대문자 문자열이다. 여러 모듈이 함께 쓰는 코드는 대상을 붙이지 않는다.

### 공통

| code | 상태 | 뜻 |
| --- | --- | --- |
| `INVALID_INPUT` | 400 | 입력 형식·범위 오류. `errors`에 필드별 사유가 있다 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | `Idempotency-Key` 헤더가 필요함 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 키로 다른 요청을 보냄 |
| `UNAUTHORIZED` | 401 | 토큰이 없거나, 서명이 틀렸거나, 만료됨 |
| `FORBIDDEN` | 403 | 권한 없음 (관리자 API) |
| `NOT_FOUND` | 404 | 존재하지 않는 경로 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드. `Allow` 헤더에 허용 메서드가 있다 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 `Content-Type` (예: JSON 대신 text/plain) |
| `INTERNAL_ERROR` | 500 | 예상하지 못한 서버 오류 |

읽을 수 없는 JSON, 없는 경로처럼 스프링이 직접 내는 오류도 이 표의 코드로 바꿔 보낸다. 표에 전용 코드가 없는 4xx는 `400 INVALID_INPUT`으로, 5xx는 `500 INTERNAL_ERROR`로 보낸다.

### 회원·인증

| code | 상태 | 뜻 |
| --- | --- | --- |
| `EMAIL_ALREADY_USED` | 409 | 활성 회원이 이미 쓰는 이메일 |
| `LOGIN_FAILED` | 401 | 이메일 또는 비밀번호가 틀림 (어느 쪽인지 알려 주지 않는다) |
| `INVALID_REFRESH_TOKEN` | 401 | refresh token이 없거나, 만료됐거나, 폐기됨 |
| `PASSWORD_MISMATCH` | 400 | 탈퇴 확인용 비밀번호가 틀림 |
| `ACTIVE_RESERVATION_EXISTS` | 409 | 결제 대기·확정 예매가 있어 탈퇴할 수 없음 |

`PASSWORD_MISMATCH`를 `401`로 하지 않은 이유: 클라이언트가 `401`을 받으면 보통 로그아웃 처리를 한다. 이 경우는 로그인 상태가 정상이고 입력만 틀린 것이다.

### 포인트

| code | 상태 | 뜻 |
| --- | --- | --- |
| `INSUFFICIENT_POINTS` | 409 | 잔액 부족 |

### 공연

| code | 상태 | 뜻 |
| --- | --- | --- |
| `CONCERT_NOT_FOUND` | 404 | 공연이 없음 (관람객에게는 공개되지 않은 공연도 포함) |
| `CONCERT_INVALID_STATUS` | 409 | 지금 상태에서 할 수 없는 동작 (예: 공개된 공연을 다시 공개) |
| `CONCERT_HAS_NO_PERFORMANCE` | 409 | 회차가 없어 공개할 수 없음 |
| `PERFORMANCE_NOT_FOUND` | 404 | 회차가 없음 |
| `PERFORMANCE_ALREADY_CANCELED` | 409 | 이미 취소된 회차 |
| `PERFORMANCE_NOT_ON_SALE` | 409 | 지금 판매 중이 아님 |
| `VENUE_NOT_FOUND` | 404 | 공연장이 없음 |

### 좌석·예매

| code | 상태 | 뜻 |
| --- | --- | --- |
| `SEAT_NOT_FOUND` | 404 | 그 회차에 없는 좌석 |
| `SEAT_NOT_AVAILABLE` | 409 | 이미 선점·판매된 좌석 |
| `PENDING_RESERVATION_EXISTS` | 409 | 같은 회차에 결제 대기 예매가 이미 있음 |
| `RESERVATION_NOT_FOUND` | 404 | 예매가 없거나 본인 예매가 아님 |
| `RESERVATION_NOT_PENDING` | 409 | 결제 대기 상태가 아니라 결제·선점 취소를 할 수 없음 |
| `RESERVATION_EXPIRED` | 409 | 선점 시간이 지나 결제할 수 없음 |

### 결제

| code | 상태 | 뜻 |
| --- | --- | --- |
| `PAYMENT_NOT_FOUND` | 404 | 결제가 없거나 본인 결제가 아님 |
| `PAYMENT_ALREADY_REFUNDED` | 409 | 이미 환불됨 |
| `REFUND_PERIOD_ENDED` | 409 | 환불 가능 기간(공연 24시간 전)이 지남 |

에러 코드를 코드에서 표현하는 방법은 [ADR 0006](adr/0006-business-errors-use-one-exception-and-module-error-codes.md)과 [개발 규칙 5절](conventions.md#5-예외와-에러-응답)에 있다.
