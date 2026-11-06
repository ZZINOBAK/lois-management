package com.lois.management.auth;

import com.lois.management.config.filter.IpWhitelistFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
    private final IpWhitelistFilter ipWhitelistFilter;
    private final EmployeeUserDetailsService employeeUserDetailsService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final CustomOAuth2Service customOAuth2Service;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1) //API 전용 체인 (JWT-only)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")

                // API는 CSRF 불필요(헤더 JWT 기반)
                .csrf(csrf -> csrf.disable())
                // 세션 사용 금지(세션이 와도 무시)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .anyRequest().hasAnyRole("ADMIN")
                )
                // JWT 필터는 API 체인에만 적용
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // API는 JSON 에러 응답
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
    @Order(2) //페이지 전용 체인 (세션 + 폼로그인)
    public SecurityFilterChain pageFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint defaultEntryPoint = new LoginUrlAuthenticationEntryPoint("/login");
        AccessDeniedHandler defaultDenied = new AccessDeniedHandlerImpl();

        http
                .securityMatcher("/**")

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/public/**",
                                "/favicon.ico",
                                "/css/**", "/js/**", "/images/**",
                                "/login",
                                "/oauth2/**", "/login/oauth2/**",
                                "/auth/after-login",
                                "/actuator/health",
                                "/access-request/**",
                                "/demo/**",
                                "/error", "/error/**"
                        ).permitAll()
                        .requestMatchers("/auth/pending").authenticated()
                        .requestMatchers("/api/**").denyAll()
                        .requestMatchers("/reservations/**", "/cake-movement/**", "/stock-requests/**", "/items/**", "/cake-stocks/**")
                        .hasAnyRole("ADMIN")
                        .anyRequest().hasAnyRole("ADMIN")
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

                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2Service)
                        )
                        .successHandler(oAuth2LoginSuccessHandler)
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
                )

                .addFilterBefore(ipWhitelistFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }
}
