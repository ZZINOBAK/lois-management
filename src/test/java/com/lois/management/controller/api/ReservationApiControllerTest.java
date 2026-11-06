package com.lois.management.controller.api;

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

@WebMvcTest(ReservationApiController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReservationApiControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ReservationService reservationService;
    @MockitoBean CakeMovementService cakeMovementService;
    @MockitoBean CakeService cakeService;
    @MockitoBean EmployeeUserDetailsService employeeUserDetailsService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean IpWhitelistFilter ipFilter;

    @BeforeEach
    void passInfrastructureFilters() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(2, FilterChain.class).doFilter(
                    invocation.getArgument(0, ServletRequest.class),
                    invocation.getArgument(1, ServletResponse.class));
            return null;
        }).when(ipFilter).doFilter(any(), any(), any());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwt(String role) {
        String token = "test-" + role;
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(token)).thenReturn(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "operator", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
        return request -> { request.addHeader("Authorization", "Bearer " + token); return request; };
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void pickupCompletesForAllowedRoles(String role) throws Exception {
        when(reservationService.pickup(7L)).thenReturn(true);
        mockMvc.perform(post("/api/reservations/pickup/7").with(jwt(role)).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Pickup-Result", "completed"));
        verify(reservationService).pickup(7L);
    }

    @Test
    void alreadyPickedUpReturnsDistinctHeader() throws Exception {
        when(reservationService.pickup(7L)).thenReturn(false);
        mockMvc.perform(post("/api/reservations/pickup/7").with(jwt("STAFF")).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Pickup-Result", "already-completed"));
    }

    @Test
    void missingReservationReturnsNotFound() throws Exception {
        when(reservationService.pickup(7L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mockMvc.perform(post("/api/reservations/pickup/7").with(jwt("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void disallowedReservationStatusReturnsConflict() throws Exception {
        when(reservationService.pickup(7L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mockMvc.perform(post("/api/reservations/pickup/7").with(jwt("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void invalidReservationIdIsRejected(String id) throws Exception {
        mockMvc.perform(post("/api/reservations/pickup/" + id).with(jwt("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void pickupOtherRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup/7").with(jwt("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }

    @Test
    void pickupAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup/7").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void pickupUsesJwtChainWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup/7").with(jwt("ADMIN")))
                .andExpect(status().isNoContent());
        verify(reservationService).pickup(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void cancelPickupCompletesForAllowedRoles(String role) throws Exception {
        when(reservationService.cancelPickup(7L, "MISTAKE")).thenReturn(true);
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "MISTAKE").with(jwt(role)).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Pickup-Cancel-Result", "completed"));
        verify(reservationService).cancelPickup(7L, "MISTAKE");
    }

    @Test
    void alreadyCancelledPickupReturnsDistinctHeader() throws Exception {
        when(reservationService.cancelPickup(7L, "MISTAKE")).thenReturn(false);
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "MISTAKE").with(jwt("STAFF")).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Pickup-Cancel-Result", "already-completed"));
    }

    @Test
    void cancelPickupMissingReservationReturnsNotFound() throws Exception {
        when(reservationService.cancelPickup(7L, "MISTAKE")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "MISTAKE").with(jwt("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelPickupConflictReturnsConflict() throws Exception {
        when(reservationService.cancelPickup(7L, "MISTAKE")).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "MISTAKE").with(jwt("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void cancelPickupInvalidIdIsRejected(String id) throws Exception {
        mockMvc.perform(post("/api/reservations/pickup-cancel/" + id).param("cancelReason", "MISTAKE")
                        .with(jwt("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void cancelPickupOtherRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "MISTAKE").with(jwt("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }

    @Test
    void cancelPickupAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "MISTAKE").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void cancelPickupUsesJwtChainWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "MISTAKE").with(jwt("ADMIN")))
                .andExpect(status().isNoContent());
        verify(reservationService).cancelPickup(7L, "MISTAKE");
    }


    @Test
    void invalidJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup/7").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }

    @Test
    void sessionAloneCannotAccessJwtApi() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "operator", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        mockMvc.perform(post("/api/reservations/pickup/7").session(session))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "returned"})
    void invalidCancelReasonDoesNotCallService(String reason) throws Exception {
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", reason).with(jwt("STAFF")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void missingCancelReasonDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").with(jwt("STAFF")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void returnReasonIsPassedToService() throws Exception {
        when(reservationService.cancelPickup(7L, "RETURNED")).thenReturn(true);
        mockMvc.perform(post("/api/reservations/pickup-cancel/7").param("cancelReason", "RETURNED").with(jwt("STAFF")))
                .andExpect(status().isNoContent());
        verify(reservationService).cancelPickup(7L, "RETURNED");
    }
}
