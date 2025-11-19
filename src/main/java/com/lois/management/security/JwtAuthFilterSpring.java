package com.lois.management.security;

import com.lois.management.domain.Employee;
import com.lois.management.service.EmployeeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilterSpring extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final EmployeeService employeeService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info(">>> [JwtAuthFilterSpring] doFilterInternal 진입");

        String header = request.getHeader("Authorization");

        // 1) 헤더 없으면 그냥 다음 필터로 패스
        if (header == null || !header.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        // 2) 토큰 꺼내기
        String token = header.substring(7);

        // 3) 토큰 유효성 검사 (기간 만료, 서명 등)
        if (!jwtProvider.validate(token)) {
            filterChain.doFilter(request, response);

            return;
        }

        // 4) 토큰에서 username(or employeeId) 꺼내기
        String employeeName = jwtProvider.getUsername(token);

        // 5) 우리 쪽 유저(직원) 정보 조회
        Employee employee = employeeService.findByEmployeeName(employeeName);
        // 메소드명은 너 프로젝트에 맞게

        // 6) 권한 목록 생성 (예: ROLE_ADMIN, ROLE_STAFF 등)
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + employee.getRole()) // 예시
        );

        // 7) Authentication 객체 생성
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        employeeName, null, authorities
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );



        // 8) SecurityContext 에 저장 (이 순간 “인증 완료” 상태가 됨)
        SecurityContextHolder.getContext().setAuthentication(authentication);


        // 9) 다음 필터로 넘기기
        filterChain.doFilter(request, response);
    }

}
