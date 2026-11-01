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

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void cancelPickupCompletesForAllowedRoles(String role) throws Exception {
        when(reservationService.cancelPickup(7L)).thenReturn(true);
        mockMvc.perform(post("/reservations/7/pickup-cancel").with(user("operator").roles(role)).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Pickup-Cancel-Result", "completed"));
        verify(reservationService).cancelPickup(7L);
    }

    @Test
    void alreadyCancelledPickupReturnsDistinctHeader() throws Exception {
        when(reservationService.cancelPickup(7L)).thenReturn(false);
        mockMvc.perform(post("/reservations/7/pickup-cancel").with(user("operator").roles("STAFF")).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Pickup-Cancel-Result", "already-completed"));
    }

    @Test
    void cancelPickupMissingReservationReturnsNotFound() throws Exception {
        when(reservationService.cancelPickup(7L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mockMvc.perform(post("/reservations/7/pickup-cancel").with(user("operator").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelPickupConflictReturnsConflict() throws Exception {
        when(reservationService.cancelPickup(7L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mockMvc.perform(post("/reservations/7/pickup-cancel").with(user("operator").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void cancelPickupInvalidIdIsRejected(String id) throws Exception {
        mockMvc.perform(post("/reservations/" + id + "/pickup-cancel")
                        .with(user("operator").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void cancelPickupOtherRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/reservations/7/pickup-cancel").with(user("other").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }

    @Test
    void cancelPickupAnonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(post("/reservations/7/pickup-cancel").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(reservationService);
    }

    @Test
    void cancelPickupRequiresCsrf() throws Exception {
        mockMvc.perform(post("/reservations/7/pickup-cancel").with(user("operator").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservationService);
    }

    @Test
    void reservationRowShowsFlavorWithoutProductionAction() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setId(7L);
        reservation.setCakeFlavor("가나슈");
        reservation.setMakeStatus("READY");
        reservation.setPickupStatus("WAITING");
        reservation.setCakeSize(2);
        reservation.setResDate(java.time.LocalDate.now());
        reservation.setResTime(java.time.LocalTime.of(13, 0));
        reservation.setContact("010-1234-0056");
        reservation.setContactSuffix("0056");
        reservation.setPaid(true);
        when(reservationService.findByContactSuffix("0056")).thenReturn(List.of(reservation));
        String html = mockMvc.perform(get("/reservations/search").param("contactSuffix", "0056")
                .with(user("staff").roles("STAFF"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(html).contains("reservation-flavor ellipsis", "가나슈", "/api/reservations/7/pickup-toggle")
                .doesNotContain("produce-toggle", "make-toggle", "flavor-btn", "케이크를 만들었나요?");
    }

    @Test
    void onSitePageTargetsNewSaleApiWithCsrf() throws Exception {
        com.lois.management.domain.Cake cake = new com.lois.management.domain.Cake();
        cake.setId(1L);
        cake.setFlavor("가나슈");
        when(reservationService.findAllCakeFlavor()).thenReturn(List.of(cake));
        String html = mockMvc.perform(get("/reservations/on-site").with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("reservation/on-site"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(html).contains("action=\"/cake-stocks/on-site-sales\"",
                "name=\"cake_id\"", "name=\"cake_size\"", "/js/auth-common.js",
                "/js/on-site-sale.js", "name=\"_csrf\"", "data-sale-feedback")
                .doesNotContain("/cake-movements/on-site", "name=\"note\"");
        java.nio.file.Path preview = java.nio.file.Path.of("build/reports/on-site-sale-preview.html");
        java.nio.file.Files.createDirectories(preview.getParent());
        java.nio.file.Files.writeString(preview, html);
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
