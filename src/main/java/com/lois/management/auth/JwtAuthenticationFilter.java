package com.lois.management.auth;

import com.lois.management.domain.Employee;
import com.lois.management.service.EmployeeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final EmployeeUserDetailsService employeeUserDetailsService;

    private String resolveToken(HttpServletRequest request) {
        // 1) Authorization 헤더 우선
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String qs = request.getQueryString();
        String referer = request.getHeader("Referer");


        log.info(">>>>> JWT필터 {} {}{} | referer={}",
                request.getMethod(),
                uri,
                (qs != null ? "?" + qs : ""),
                referer);

        // ✅ 헤더(Bearer) 또는 쿠키(LOIS_ACCESS_TOKEN)에서 토큰 찾기
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // JWT 발급 API는 JWT 필터 적용 제외 (세션 인증을 그대로 쓰게)
        if (uri.equals("/api/auth/token")) return true;


        boolean skip =
                uri.equals("/") ||
                        uri.startsWith("/css/") ||
                        uri.startsWith("/js/") ||
                        uri.startsWith("/images/") ||
                        uri.startsWith("/swagger-ui/") ||
                        !uri.startsWith("/api/");

        if (skip) {
            log.info(">>>>> JWT필터 스킵하기 {}", uri);
        }
        return skip;
    }
}
