package com.ticatch.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "지금"은 이 Clock에서 얻는다(ADR 0005). 테스트에서 시각을 고정할 수 있게 하려는 것이다. */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
