package com.ticatch.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 작업을 켠다. 예: 선점 만료. */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
