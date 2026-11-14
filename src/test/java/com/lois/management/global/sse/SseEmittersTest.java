package com.lois.management.global.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SseEmittersTest {

    @Test
    void dataChangedEventIsSentToEveryEmitter() throws Exception {
        SseEmitters sseEmitters = new SseEmitters();
        SseEmitter first = sseEmitters.add(mock(SseEmitter.class));
        SseEmitter second = sseEmitters.add(mock(SseEmitter.class));

        sseEmitters.onDataChanged(new DataChangedEvent());

        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verify(second).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void emitterIsRemovedWhenSendFails() throws Exception {
        SseEmitters sseEmitters = new SseEmitters();
        SseEmitter broken = sseEmitters.add(mock(SseEmitter.class));
        SseEmitter healthy = sseEmitters.add(mock(SseEmitter.class));
        doThrow(new IOException("broken pipe")).when(broken).send(any(SseEmitter.SseEventBuilder.class));

        sseEmitters.onDataChanged(new DataChangedEvent());

        assertThat(sseEmitters.size()).isEqualTo(1);
        verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
    }
}
