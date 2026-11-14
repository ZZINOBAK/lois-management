package com.lois.management.global.sse;

import com.lois.management.domain.ipaccess.filter.IpWhitelistFilter;
import com.lois.management.global.auth.CustomOAuth2Service;
import com.lois.management.global.auth.EmployeeUserDetailsService;
import com.lois.management.global.auth.JwtAuthenticationFilter;
import com.lois.management.global.auth.LoginSuccessHandler;
import com.lois.management.global.auth.OAuth2LoginSuccessHandler;
import com.lois.management.global.auth.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SseController.class)
@Import(SecurityConfig.class)
class SseControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    SseEmitters sseEmitters;
    @MockitoBean
    JwtAuthenticationFilter jwtFilter;
    @MockitoBean
    IpWhitelistFilter ipFilter;
    @MockitoBean
    EmployeeUserDetailsService users;
    @MockitoBean
    LoginSuccessHandler login;
    @MockitoBean
    CustomOAuth2Service oAuth2Service;
    @MockitoBean
    OAuth2LoginSuccessHandler oAuth2Login;
    @MockitoBean
    ClientRegistrationRepository clientRegistrations;

    @BeforeEach
    void passInfrastructureFilters() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(2, FilterChain.class).doFilter(
                    invocation.getArgument(0, ServletRequest.class),
                    invocation.getArgument(1, ServletResponse.class));
            return null;
        }).when(ipFilter).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            invocation.getArgument(2, FilterChain.class).doFilter(
                    invocation.getArgument(0, ServletRequest.class),
                    invocation.getArgument(1, ServletResponse.class));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void anonymousSubscribeRedirectsToLogin() throws Exception {
        mvc.perform(get("/sse/subscribe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(sseEmitters);
    }

    @Test
    void adminSubscribeOpensUnbufferedEventStream() throws Exception {
        // 실제 subscribe()처럼 연결 직후 comment를 먼저 전송한 emitter를 반환
        SseEmitter emitter = new SseEmitter();
        emitter.send(SseEmitter.event().comment("connected"));
        when(sseEmitters.subscribe()).thenReturn(emitter);

        mvc.perform(get("/sse/subscribe").with(user("admin").roles("ADMIN")))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
        verify(sseEmitters).subscribe();
    }
}
