package com.lois.management.global.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
// 이전 클래스명: com.lois.management.security.OAuth2LoginSuccessHandler
// 이식 출처: feature/security-spring / src/main/java/com/lois/management/security/OAuth2LoginSuccessHandler.java
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User =
                (OAuth2User) authentication.getPrincipal();

        String role = oAuth2User.getAttribute("role");

        if ("PENDING".equals(role)) {
            response.sendRedirect("/auth/pending");
            return;
        }

        response.sendRedirect("/");
    }
}

