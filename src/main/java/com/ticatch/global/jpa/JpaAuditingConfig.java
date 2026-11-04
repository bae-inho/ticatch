package com.ticatch.global.jpa;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 엔티티의 생성·수정 시각을 자동으로 채운다. 시각은 애플리케이션의 {@link Clock}에서 얻는다.
 *
 * <p>애플리케이션 클래스가 아니라 별도 설정에 둔다. {@code @WebMvcTest}처럼 JPA를 띄우지 않는 테스트가 이 설정을 읽지 않게 하려는 것이다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaAuditingConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
