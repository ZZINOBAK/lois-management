package com.lois.management.global.sse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
public class SseEmitters {
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L; // 연결 유지 시간, 만료 시 브라우저가 자동 재연결
    private static final long HEARTBEAT_SECONDS = 25;           // nginx 기본 read timeout(60초)보다 짧게

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    // heartbeat 전용 스레드 1개 (전역 @EnableScheduling 미사용)
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    void startHeartbeat() {
        heartbeat.scheduleAtFixedRate(() -> sendToAll(() -> SseEmitter.event().comment("heartbeat")),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeat.shutdownNow();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = add(new SseEmitter(TIMEOUT_MILLIS));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    SseEmitter add(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @EventListener
    public void onDataChanged(DataChangedEvent event) {
        sendToAll(() -> SseEmitter.event().name("changed").data("changed"));
    }

    // SseEventBuilder는 전송 시 내부 상태가 바뀌므로 연결마다 새로 생성
    private void sendToAll(Supplier<SseEmitter.SseEventBuilder> event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event.get());
            } catch (IOException | IllegalStateException e) {
                // 끊어진 연결은 목록에서 제거
                emitters.remove(emitter);
                log.debug("SSE 전송 실패로 연결 제거 exceptionType={}", e.getClass().getName());
            }
        }
    }

    int size() {
        return emitters.size();
    }
}
