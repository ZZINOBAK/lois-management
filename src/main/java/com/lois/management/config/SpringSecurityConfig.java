package com.lois.management.config;

import com.lois.management.security.EmployeeUserDetailsService;
import com.lois.management.security.JwtAuthFilterSpring;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {

    private final JwtAuthFilterSpring jwtAuthFilterSpring;
    private final EmployeeUserDetailsService employeeUserDetailsService;



    @Bean
    public SecurityFilterChain securityFilterChainUserDetails(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/css/**", "/js/**", "/images/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
//                .exceptionHandling(ex -> ex
//                        .authenticationEntryPoint((request, response, authException) -> {
//                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                            response.setContentType("application/json;charset=UTF-8");
//                            response.getWriter().write("""
//                                    {"code":401,"message":"인증이 필요합니다. JWT 토큰을 포함해 주세요."}
//                                    """);
//                        })
//                        .accessDeniedHandler((request, response, accessDeniedException) -> {
//                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//                            response.setContentType("application/json;charset=UTF-8");
//                            response.getWriter().write("""
//                                    {"code":403,"message":"접근 권한이 없습니다."}
//                                    """);
//                        })
//                )
                // 🔵 여기서부터 formLogin 활성화
                .userDetailsService(employeeUserDetailsService)
                .formLogin(form -> form
//                        .loginPage("/login")           // 커스텀 로그인 페이지 URL (없으면 기본 폼 사용)
                        .loginProcessingUrl("/login")  // form action이 이 URL로 POST
                        .defaultSuccessUrl("/", true)  // 로그인 성공 시 이동할 기본 URL
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                )
                .httpBasic(basic -> basic.disable());

        http.addFilterBefore(jwtAuthFilterSpring, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//
//        http
//                .csrf(csrf -> csrf.disable()) // 우선 API 테스트 편하게 CSRF 비활성화
//
//
//                .authorizeHttpRequests(auth -> auth
//                        // 1) 로그인, JWT 로그인, 정적 리소스 등은 모두 허용
//                        .requestMatchers(
//                                "/api/auth/**",
//                                "/swagger-ui/**", "/v3/api-docs/**",
//                                "/css/**", "/js/**", "/images/**"
//                        ).permitAll()
//
//                        // 관리자 전용 *추가 코드
//                        .requestMatchers("/api/test/admin-ping").hasRole("ADMIN")
//
//                        // 직원 정보 조회 (로그인만) *추가 코드
//                        .requestMatchers("/api/employees/me/**").authenticated()
//
//                        // 2) 나머지 /api/** 는 일단 “인증 필요”로 표시만
//                        .requestMatchers("/api/**").authenticated()
//
//                        // 3) 그 외 일반 페이지(예약 화면 등)는 일단 열어둠
//                        .anyRequest().permitAll()
//                )
//
//                .exceptionHandling(ex -> ex
//                        .authenticationEntryPoint((request, response, authException) -> {
//                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                            response.setContentType("application/json;charset=UTF-8");
//                            response.getWriter().write("""
//                                    {"code":401,"message":"인증이 필요합니다. JWT 토큰을 포함해 주세요."}
//                                    """);
//                        })
//                        .accessDeniedHandler((request, response, accessDeniedException) -> {
//                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//                            response.setContentType("application/json;charset=UTF-8");
//                            response.getWriter().write("""
//                                    {"code":403,"message":"접근 권한이 없습니다."}
//                                    """);
//                        })
//                )
//
//                // 나중에 폼 로그인 / OAuth2 로그인 붙일 자리
//                .formLogin(form -> form.disable())   // 일단 기본 로그인폼은 끄고
//                .httpBasic(basic -> basic.disable()); // Basic 인증도 잠깐 끔
//
//        // 🔥 여기서 JwtAuthFilter를 UsernamePasswordAuthenticationFilter 앞에 넣는 게 핵심
//        http.addFilterBefore(jwtAuthFilterSpring, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }
}
