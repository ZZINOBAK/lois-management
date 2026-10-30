package com.lois.management.controller;

import com.lois.management.auth.*;
import com.lois.management.config.filter.IpWhitelistFilter;
import com.lois.management.service.CakeStockService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CakeStockController.class)
@Import(SecurityConfig.class)
class CakeStockControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CakeStockService service;
    @MockitoBean JwtAuthenticationFilter jwtFilter;
    @MockitoBean IpWhitelistFilter ipFilter;
    @MockitoBean EmployeeUserDetailsService users;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;

    @BeforeEach
    void passInfrastructureFilters() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(2, FilterChain.class).doFilter(
                    invocation.getArgument(0, ServletRequest.class),
                    invocation.getArgument(1, ServletResponse.class));
            return null;
        }).when(ipFilter).doFilter(any(), any(), any());
        // MockMvc also registers filter beans outside the security chains.
        doAnswer(invocation -> {
            invocation.getArgument(2, FilterChain.class).doFilter(
                    invocation.getArgument(0, ServletRequest.class),
                    invocation.getArgument(1, ServletResponse.class));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void allowedRolesReceiveNoContent(String role) throws Exception {
        mvc.perform(post("/cake-stocks/produce").with(user("operator").roles(role)).with(csrf())
                .param("cake_id", "1").param("cake_size", "2"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).produce(1L, 2);
    }

    @Test
    void otherRoleIsForbidden() throws Exception {
        mvc.perform(post("/cake-stocks/produce").with(user("other").roles("USER")).with(csrf())
                .param("cake_id", "1").param("cake_size", "2")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(post("/cake-stocks/produce").with(csrf())
                .param("cake_id", "1").param("cake_size", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(service);
    }

    @Test
    void missingCsrfIsForbidden() throws Exception {
        mvc.perform(post("/cake-stocks/produce").with(user("operator").roles("ADMIN"))
                .param("cake_id", "1").param("cake_size", "2")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void invalidCakeIdIsRejected(String id) throws Exception {
        mvc.perform(post("/cake-stocks/produce").with(user("operator").roles("ADMIN")).with(csrf())
                .param("cake_id", id).param("cake_size", "2")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "abc"})
    void invalidSizeIsRejected(String size) throws Exception {
        mvc.perform(post("/cake-stocks/produce").with(user("operator").roles("ADMIN")).with(csrf())
                .param("cake_id", "1").param("cake_size", size)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cake_id", "cake_size"})
    void missingParameterIsRejected(String missing) throws Exception {
        var request = post("/cake-stocks/produce").with(user("operator").roles("ADMIN")).with(csrf());
        if (!missing.equals("cake_id")) request.param("cake_id", "1");
        if (!missing.equals("cake_size")) request.param("cake_size", "2");
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void cancellationAllowedRolesReceiveNoContent(String role) throws Exception {
        when(service.cancelProduction(1L, 2)).thenReturn(true);
        mvc.perform(post("/cake-stocks/produce-cancel").with(user("operator").roles(role)).with(csrf())
                .param("cake_id", "1").param("cake_size", "2"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).cancelProduction(1L, 2);
    }

    @Test
    void cancellationWithoutStockReturnsConflictWithReason() throws Exception {
        when(service.cancelProduction(1L, 2)).thenReturn(false);
        mvc.perform(post("/cake-stocks/produce-cancel").with(user("operator").roles("STAFF")).with(csrf())
                .param("cake_id", "1").param("cake_size", "2"))
                .andExpect(status().isConflict())
                .andExpect(status().reason("재고가 없어 제작을 취소할 수 없습니다."));
    }

    @Test
    void cancellationOtherRoleIsForbidden() throws Exception {
        mvc.perform(post("/cake-stocks/produce-cancel").with(user("other").roles("USER")).with(csrf())
                .param("cake_id", "1").param("cake_size", "2")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void cancellationAnonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(post("/cake-stocks/produce-cancel").with(csrf())
                .param("cake_id", "1").param("cake_size", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(service);
    }

    @Test
    void cancellationMissingCsrfIsForbidden() throws Exception {
        mvc.perform(post("/cake-stocks/produce-cancel").with(user("operator").roles("ADMIN"))
                .param("cake_id", "1").param("cake_size", "2")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void cancellationInvalidCakeIdIsRejected(String id) throws Exception {
        mvc.perform(post("/cake-stocks/produce-cancel").with(user("operator").roles("ADMIN")).with(csrf())
                .param("cake_id", id).param("cake_size", "2")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "abc"})
    void cancellationInvalidSizeIsRejected(String size) throws Exception {
        mvc.perform(post("/cake-stocks/produce-cancel").with(user("operator").roles("ADMIN")).with(csrf())
                .param("cake_id", "1").param("cake_size", size)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cake_id", "cake_size"})
    void cancellationMissingParameterIsRejected(String missing) throws Exception {
        var request = post("/cake-stocks/produce-cancel").with(user("operator").roles("ADMIN")).with(csrf());
        if (!missing.equals("cake_id")) request.param("cake_id", "1");
        if (!missing.equals("cake_size")) request.param("cake_size", "2");
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
