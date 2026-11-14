package com.lois.management.global.sse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

// 쓰기 요청 성공 시 데이터 변경 이벤트 발행
@Component
@RequiredArgsConstructor
public class SseNotifyInterceptor implements HandlerInterceptor {
    // 예약·재고 데이터를 변경하는 경로
    public static final String[] PATH_PATTERNS = {
            "/reservations/**", "/api/reservations/**", "/cake-stocks/**", "/cake-movements/**"
    };
    // 예약 단계 화면은 세션 값만 저장하므로 제외
    public static final String[] EXCLUDE_PATH_PATTERNS = {"/reservations/step/**"};

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (ex != null || response.getStatus() >= 400) return;
        if (!WRITE_METHODS.contains(request.getMethod())) return;
        eventPublisher.publishEvent(new DataChangedEvent());
    }
}
