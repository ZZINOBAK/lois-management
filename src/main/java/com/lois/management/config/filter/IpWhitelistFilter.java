package com.lois.management.config.filter;

import com.lois.management.config.IpWhitelistProperties;
import com.lois.management.service.IpAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class IpWhitelistFilter extends OncePerRequestFilter {
    private final IpWhitelistProperties properties;
    private final IpAccessService ipAccessService;


    public IpWhitelistFilter(IpWhitelistProperties properties,
                             IpAccessService ipAccessService) {
        this.properties = properties;
        this.ipAccessService = ipAccessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1) 공개 경로는 무조건 통과 (이미 잘 동작 중인 isPublicPath 사용)
        if (isPublicPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        // 2) yml allowed 리스트 우선 체크
        List<String> allowed = properties.getAllowed();
        if (allowed != null && allowed.contains(clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3) DB에서 APPROVED 상태인지 확인
        if (ipAccessService.isApproved(clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4) 여기까지 오면: 허용되지 않은 IP
        //    → 접근 요청 페이지로 리다이렉트 (이제 403 대신)
        String redirectUrl = "/access-request?ip=" + clientIp;
        response.sendRedirect(redirectUrl);
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        List<String> publicPaths = properties.getPublicPaths();

        if (publicPaths == null) {
            return false;
        }

        for (String path : publicPaths) {
            if ("/".equals(path)) {
                // 루트는 "완전히 / 일 때만" 공개
                if ("/".equals(uri)) {
                    return true;
                }
            } else {
                // 나머지는 prefix 매칭
                if (uri.startsWith(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
