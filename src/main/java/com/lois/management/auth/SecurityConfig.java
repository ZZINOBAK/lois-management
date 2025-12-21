package com.lois.management.auth;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EmployeeUserDetailsService employeeUserDetailsService;
    private final LoginSuccessHandler loginSuccessHandler; // 🔥 추가


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

//    @Bean
//    public SecurityFilterChain securityFilterChainUserDetails(HttpSecurity http) throws Exception {
//        http
//// ✅ CSRF 설정
//                .csrf(csrf -> csrf
//                        .ignoringRequestMatchers("/api/**") // 🔥 API만 CSRF 제외
//                )
//                .authorizeHttpRequests(auth -> auth
//                        // ✅ 공개
//                        .requestMatchers(
//                                "/", "/public/**",
//                                "/favicon.ico",
//                                "/css/**", "/js/**", "/images/**",
//                                "/login"
//                        ).permitAll()
//
//                        // ✅ 나머지는 공개로 둘지/보호할지 정책 결정
//                        .anyRequest().authenticated()   // <-- 여기 중요
//                )
//                .userDetailsService(employeeUserDetailsService)
//                .formLogin(form -> form
//                        .loginPage("/login")          // 로그인 페이지 컨트롤러에서 반환
//                        .loginProcessingUrl("/login") // 폼 action
//                        .defaultSuccessUrl("/", true)  // 항상 /로
////                        .successHandler(loginSuccessHandler)
//                        .failureHandler((req, res, ex) -> {
//                            System.out.println("LOGIN FAIL ex=" + ex.getClass().getName() + " msg=" + ex.getMessage());
//                            res.sendRedirect("/login?error");
//                        })
//                        .permitAll()
//                )
//                .logout(logout -> logout
//                        .logoutUrl("/logout")
//                        .logoutSuccessUrl("/login?logout")
//                )
//                .httpBasic(basic -> basic.disable());
//
//        AuthenticationEntryPoint defaultEntryPoint = new LoginUrlAuthenticationEntryPoint("/login");
//        AccessDeniedHandler defaultDenied = new AccessDeniedHandlerImpl();
//
//                http.exceptionHandling(e -> e
//                        .authenticationEntryPoint((request, response, authException) -> {
//                            if (request.getRequestURI().startsWith("/api/")) {
//                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
//                                response.setContentType("application/json;charset=UTF-8");
//                                response.getWriter().write("{\"message\":\"LOGIN_REQUIRED\"}");
//                            }
//
//                            defaultEntryPoint.commence(request, response, authException); // ✅ 기본 동작에 위임
//                        })
//
//                        .accessDeniedHandler((request, response, accessDeniedException) -> {
//                            if (request.getRequestURI().startsWith("/api/")) {
//                                response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
//                                response.setContentType("application/json;charset=UTF-8");
//                                response.getWriter().write("{\"message\":\"FORBIDDEN\"}");
//                            }
//
//                            defaultDenied.handle(request, response, accessDeniedException); // ✅ 기본 동작에 위임
//
//                        })
//
//
//                );
//
//        // 🔑 세션 로그인 필터(UsernamePasswordAuthenticationFilter) 앞에 JWT 필터 추가
//        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }

    @Bean
    @Order(1) //API 전용 체인 만들기 (JWT-only)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")

                // ✅ API는 CSRF 불필요(헤더 JWT 기반)
                .csrf(csrf -> csrf.disable())

                // ✅ 세션 사용 금지(세션이 와도 무시)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ✅ 인증 규칙: API는 기본적으로 인증 필요
                .authorizeHttpRequests(auth -> auth
                        // 토큰 발급 엔드포인트가 세션 기반이라면 여기서 permitAll 하지 말고,
                        // 세션으로만 호출 가능한 별도 처리(아래 참고) 필요
                        .anyRequest().authenticated()
                )

                // ✅ JWT 필터는 API 체인에만 적용
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // ✅ API는 JSON 에러 응답
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"LOGIN_REQUIRED\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"FORBIDDEN\"}");
                        })
                );

        return http.build();
    }


    @Bean
    @Order(2) //페이지 전용 체인 만들기 (세션 + 폼로그인)
    public SecurityFilterChain pageFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint defaultEntryPoint = new LoginUrlAuthenticationEntryPoint("/login");
        AccessDeniedHandler defaultDenied = new AccessDeniedHandlerImpl();

        http
                .securityMatcher("/**")

//                // ✅ 페이지는 CSRF 켜는 게 정석
//                .csrf(csrf -> csrf.enable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/public/**",
                                "/favicon.ico",
                                "/css/**", "/js/**", "/images/**",
                                "/login",
                                "/auth/after-login" // ✅ 추가
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .userDetailsService(employeeUserDetailsService)

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
//                        .defaultSuccessUrl("/", true)
                        .successHandler(loginSuccessHandler) // ✅ 여기
                        .failureHandler((req, res, ex) -> {
                            System.out.println("LOGIN FAIL ex=" + ex.getClass().getName() + " msg=" + ex.getMessage());
                            res.sendRedirect("/login?error");
                        })
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                )

                .httpBasic(basic -> basic.disable())

                // ✅ 페이지 예외는 기본 동작 유지(redirect/403)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> defaultEntryPoint.commence(req, res, ex))
                        .accessDeniedHandler((req, res, ex) -> defaultDenied.handle(req, res, ex))
                );

        return http.build();
    }
}
