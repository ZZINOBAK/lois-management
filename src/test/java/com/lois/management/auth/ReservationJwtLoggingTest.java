package com.lois.management.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lois.management.config.JwtProperties;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
@Isolated("stdout/stderr 캡처와 SecurityContext가 다른 테스트에 영향을 주지 않도록 격리")
class ReservationJwtLoggingTest {
    @ParameterizedTest(name = "validToken={0}")
    @ValueSource(booleans = {true, false})
    void creationRequestDoesNotLogTokenQueryRefererOrExceptionMessage(boolean validToken, CapturedOutput captured) throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("a-secret-key-with-at-least-32-bytes-for-tests");
        properties.setExpiration(60_000);
        JwtTokenProvider provider = new JwtTokenProvider(properties);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, mock(EmployeeUserDetailsService.class));
        ListAppender<ILoggingEvent> logs = new ListAppender<>(); logs.start();
        Logger logger = (Logger) LoggerFactory.getLogger("com.lois.management.auth");
        var previousContext = SecurityContextHolder.getContext();
        logger.addAppender(logs);
        try {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            String valid = provider.createToken("PRIVATE_NAME", "USER");
            String invalid = "PRIVATE_JWT";
            String token = validToken ? valid : invalid;
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reservations");
            request.addHeader("Authorization", "Bearer " + token);
            request.addHeader("Referer", "https://example.test/PRIVATE_REFERER");
            request.setQueryString("name=PRIVATE_NAME&phone=PRIVATE_PHONE");
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(SecurityContextHolder.getContext().getAuthentication() != null).isEqualTo(validToken);
            String output = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b) -> a + b);
            if (!validToken) {
                assertThat(output).contains("JWT 검증 실패 exceptionType=");
            }
            assertThat(output).as("SLF4J").doesNotContain(valid, invalid, "PRIVATE_");
            assertThat(captured.getOut()).as("stdout").doesNotContain(valid, invalid, "PRIVATE_");
            assertThat(captured.getErr()).as("stderr").doesNotContain(valid, invalid, "PRIVATE_");
            assertThat(logs.list).allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
        } finally {
            SecurityContextHolder.setContext(previousContext);
            logger.detachAppender(logs);
            logs.stop();
        }
    }
}
