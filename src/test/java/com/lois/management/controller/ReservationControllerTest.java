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
    @ValueSource(strings = {"RESERVED", "PICKED_UP"})
    void reservationRowShowsFlavorWithoutProductionAction(String state) throws Exception {
        Reservation reservation = new Reservation();
        reservation.setId(7L);
        reservation.setCakeFlavor("가나슈");
        reservation.setStatus(state);
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
        org.assertj.core.api.Assertions.assertThat(html).contains("reservation-flavor ellipsis", "가나슈", "/api/reservations/pickup/7", "/api/reservations/pickup-cancel/7", state.equals("PICKED_UP") ? "픽업완료" : "픽업예정")
                .doesNotContain("pickup-toggle", "픽업 불가", "produce-toggle", "make-toggle", "flavor-btn", "케이크를 만들었나요?");
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
    @Test
    void dashboardRendersCancelChoicesAndPictures() throws Exception {
        mockMvc.perform(get("/reservations").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("아직 픽업 전이에요"),
                        org.hamcrest.Matchers.containsString("케이크를 돌려받았어요"),
                        org.hamcrest.Matchers.containsString("data-cancel-reason=\"MISTAKE\""),
                        org.hamcrest.Matchers.containsString("data-cancel-reason=\"RETURNED\""),
                        org.hamcrest.Matchers.containsString("<svg"))));
    }
}
