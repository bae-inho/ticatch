package com.ticatch.global.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증·인가 설정(ADR 0007, docs/api.md 1절).
 *
 * <p>요청마다 {@code Authorization: Bearer <access token>}을 검증한다. 서버는 로그인 상태(세션)를 저장하지 않는다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** access token에 회원 역할(USER, ADMIN)을 담는 claim 이름. */
    public static final String ROLE_CLAIM = "role";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/auth/login", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/concerts/**", "/api/v1/performances/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 로드밸런서의 상태 확인과, 스프링이 오류를 내부 전달하는 /error는 토큰 없이 연다.
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // 저장값 앞에 {bcrypt}처럼 알고리즘 이름을 붙인다. 나중에 알고리즘을 바꿔도 기존 비밀번호를 검증할 수 있다.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
