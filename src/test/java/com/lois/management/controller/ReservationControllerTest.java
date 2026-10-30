package com.lois.management.controller;

import com.lois.management.auth.*;
import com.lois.management.config.filter.IpWhitelistFilter;
import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationPageReq;
import com.lois.management.service.CakeMovementService;
import com.lois.management.service.CakeService;
import com.lois.management.service.ReservationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ReservationService reservationService;
    @MockitoBean CakeMovementService cakeMovementService;
    @MockitoBean CakeService cakeService;
    @MockitoBean EmployeeUserDetailsService employeeUserDetailsService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean JwtAuthenticationFilter jwtFilter;
    @MockitoBean IpWhitelistFilter ipFilter;

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

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void pickupCompletesForAllowedRoles(String role) throws Exception {
        when(reservationService.pickup(7L)).thenReturn(true);
        mockMvc.perform(post("/reservations/7/pickup").with(user("operator").roles(role)).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Pickup-Result", "completed"));
        verify(reservationService).pickup(7L);
    }

    @Test
    void alreadyPickedUpReturnsDistinctHeader() throws Exception {
        when(reservationService.pickup(7L)).thenReturn(false);
        mockMvc.perform(post("/reservations/7/pickup").with(user("operator").roles("STAFF")).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Pickup-Result", "already-completed"));
    }

    @Test
    void missingReservationReturnsNotFound() throws Exception {
        when(reservationService.pickup(7L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mockMvc.perform(post("/reservations/7/pickup").with(user("operator").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void disallowedReservationStatusReturnsConflict() throws Exception {
        when(reservationService.pickup(7L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mockMvc.perform(post("/reservations/7/pickup").with(user("operator").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void invalidReservationIdIsRejected(String id) throws Exception {
        mockMvc.perform(post("/reservations/" + id + "/pickup").with(user("operator").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void pickupOtherRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/reservations/7/pickup").with(user("other").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }

    @Test
    void pickupAnonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(post("/reservations/7/pickup").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(reservationService);
    }

    @Test
    void pickupRequiresCsrf() throws Exception {
        mockMvc.perform(post("/reservations/7/pickup").with(user("operator").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }

    @Disabled("Security/CSRF/Thymeleaf 적용 후 테스트 환경 재정비 필요")
    @Test
    void 대시보드_조회시_예약목록과_뷰를_반환한다() throws Exception {
        // given
        Reservation r = new Reservation();
        r.setId(1L);
        ReservationPageReq reservationPageReq = new ReservationPageReq();

        given(reservationService.findAll(reservationPageReq))
                .willReturn(List.of(r));

        // when & then
        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservation/dashboard"))
                .andExpect(model().attributeExists("reservations"))
                .andExpect(model().attribute("reservations", hasSize(1)));

        verify(reservationService).findAll(reservationPageReq);
    }
}
