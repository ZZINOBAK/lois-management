package com.lois.management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SpringSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable()) // 우선 API 테스트 편하게 CSRF 비활성화
                .authorizeHttpRequests(auth -> auth
                        // 1) 로그인, JWT 로그인, 정적 리소스 등은 모두 허용
                        .requestMatchers(
                                "/api/auth/**",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/css/**", "/js/**", "/images/**"
                        ).permitAll()

                        // 2) 나머지 /api/** 는 일단 “인증 필요”로 표시만
                        .requestMatchers("/api/**").authenticated()

                        // 3) 그 외 일반 페이지(예약 화면 등)는 일단 열어둠
                        .anyRequest().permitAll()
                )
                // 나중에 폼 로그인 / OAuth2 로그인 붙일 자리
                .formLogin(form -> form.disable())   // 일단 기본 로그인폼은 끄고
                .httpBasic(basic -> basic.disable()); // Basic 인증도 잠깐 끔

        return http.build();
    }
}
