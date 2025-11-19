package com.lois.management.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

//@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements Filter {

    private final JwtProvider jwtProvider;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info(">>> [JwtAuthFilter] doFilter 진입");

        HttpServletRequest req = (HttpServletRequest) request;
        String header = req.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtProvider.validate(token)) {
                String username = jwtProvider.getUsername(token);
                String role = jwtProvider.getRole(token);

                req.setAttribute("username", username);
                req.setAttribute("role", role); // 🔥 역할도 심어두기

            }
        }

        chain.doFilter(request, response);
    }

}
