package com.lois.management.global.sse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {
    private final SseEmitters sseEmitters;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 실시간 변경 알림 구독
    public SseEmitter subscribe(HttpServletResponse response) {
        // nginx가 이 응답만 버퍼링하지 않고 즉시 전달하도록 지정
        response.setHeader("X-Accel-Buffering", "no");
        return sseEmitters.subscribe();
    }
}
