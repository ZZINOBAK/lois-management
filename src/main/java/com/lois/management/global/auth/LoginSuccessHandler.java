package com.lois.management.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // 원래 접근하려던 URL 기억 (없으면 "/")
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        String next = (savedRequest != null) ? savedRequest.getRedirectUrl() : "/";

        // after-login 페이지에서 next로 이동하게 전달
        String encoded = URLEncoder.encode(next, StandardCharsets.UTF_8);

        response.sendRedirect("/auth/after-login?next=" + encoded);
    }

    // 쿠키용 핸들러 메소드
//    @Override
//    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
//
//        log.info("✅ 로그인 성공, JWT 발급 시작");
//
//        // principal 이 EmployeeUserDetails 라고 가정
//        EmployeeUserDetails principal = (EmployeeUserDetails) authentication.getPrincipal();
//
//        String username = principal.getEmployee().getEmployeeName(); // or getUsername()
//        String role = principal.getEmployee().getRole();             // 예: "ADMIN"
//
//        // 🔑 JWT 생성 (role 은 "ADMIN" 같은 값, 내부에서 "ROLE_" 붙여서 사용 중)
//        String token = jwtTokenProvider.createToken(username, role);
//
//        log.info("✅ JWT 생성 완료, username={}, role={}", username, role);
//
//        // 🔒 HttpOnly 쿠키로 JWT 내려보내기
//        Cookie jwtCookie = new Cookie("LOIS_ACCESS_TOKEN", token);
//        jwtCookie.setPath("/");          // 모든 경로에서 전송
//        jwtCookie.setHttpOnly(true);     // JS 에서 접근 불가 (보안용)
//        // HTTPS 쓸 때만 true 로 (개발 단계면 false 로 둬도 됨)
//        // jwtCookie.setSecure(true);
//
//        // 쿠키 추가
//        response.addCookie(jwtCookie);
//
//        // 로그인 성공 후 원하는 페이지로 리다이렉트
//        response.sendRedirect("/");
//    }
}
