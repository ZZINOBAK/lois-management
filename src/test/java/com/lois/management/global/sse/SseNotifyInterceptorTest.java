package com.lois.management.global.sse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SseNotifyInterceptorTest {
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final SseNotifyInterceptor interceptor = new SseNotifyInterceptor(publisher);

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void successfulWritePublishesDataChangedEvent(String method) {
        interceptor.afterCompletion(new MockHttpServletRequest(method, "/reservations/1"), response(200), null, null);

        verify(publisher).publishEvent(any(DataChangedEvent.class));
    }

    @Test
    void readRequestDoesNotPublish() {
        interceptor.afterCompletion(new MockHttpServletRequest("GET", "/reservations/list"), response(200), null, null);

        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 409, 500})
    void errorStatusDoesNotPublish(int status) {
        interceptor.afterCompletion(new MockHttpServletRequest("POST", "/cake-stocks/produce"), response(status), null, null);

        verifyNoInteractions(publisher);
    }

    @Test
    void exceptionDuringRequestDoesNotPublish() {
        interceptor.afterCompletion(new MockHttpServletRequest("PATCH", "/reservations/1"), response(200), null,
                new IllegalStateException("failed"));

        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reservations/1", "/api/reservations/pickup/1", "/cake-stocks/produce", "/cake-movements/1/produce"})
    void dataChangePathsAreIntercepted(String path) {
        assertThat(mapped().matches(parsed(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reservations/step/1", "/demo/reservations", "/login", "/stock-requests"})
    void excludedOrUnrelatedPathsAreNotIntercepted(String path) {
        assertThat(mapped().matches(parsed(path))).isFalse();
    }

    private MappedInterceptor mapped() {
        return new MappedInterceptor(SseNotifyInterceptor.PATH_PATTERNS, SseNotifyInterceptor.EXCLUDE_PATH_PATTERNS, interceptor);
    }

    private MockHttpServletRequest parsed(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private MockHttpServletResponse response(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        return response;
    }
}
