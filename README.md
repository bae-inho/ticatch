# Ticatch

콘서트 좌석 예매 백엔드다. 인기 공연의 예매가 열리는 순간 같은 좌석에 요청이 몰려도 한 좌석은 한 사람에게만 팔리도록 하고, 몰리는 요청을 얼마나 버티는지 측정하고 개선한다.

## 기술 스택

- Java 25, Spring Boot 4.1, Spring Data JPA
- PostgreSQL 17, Flyway
- JUnit 5, Testcontainers
- Docker Compose, GitHub Actions

## 실행

필요한 것: JDK 25, Docker.

1. PostgreSQL을 띄운다. 데이터는 `postgres-data` 볼륨에 남는다.

   ```bash
   docker compose up -d
   ```

2. 애플리케이션을 실행한다. 처음 실행할 때 Flyway가 테이블을 만든다.

   ```bash
   ./gradlew bootRun
   ```

3. 다른 터미널에서 확인한다. `{"status":"UP"}`이 나오면 성공이다.

   ```bash
   curl http://localhost:8080/actuator/health
   ```

DB 접속 정보는 [application.yml](src/main/resources/application.yml)의 기본값을 쓴다. 다른 DB에 붙으려면 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수를 넣는다.

## 테스트

```bash
./gradlew test
```

Testcontainers가 PostgreSQL 컨테이너를 직접 띄우므로 Docker가 실행 중이어야 한다. `docker compose`로 띄운 DB는 쓰지 않는다.

## 문서

| 알고 싶은 것 | 문서 |
| --- | --- |
| 무엇을 만드는가, 규칙은 무엇인가 | [요구사항](docs/requirements.md) |
| 개념, 상태 전이, ERD, 불변식 | [도메인 설계](docs/domain.md) |
| API 규약, 엔드포인트, 에러 코드 | [API 설계](docs/api.md) |
| 패키지, 의존 규칙, 이름, 테스트, 커밋 | [개발 규칙](docs/conventions.md) |
| 왜 이렇게 설계했는가 | [ADR](docs/adr/README.md) |
