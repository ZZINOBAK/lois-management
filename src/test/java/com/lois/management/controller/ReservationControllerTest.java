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
    @MockitoBean com.lois.management.service.CakeProductionService cakeProductionService;
    @MockitoBean EmployeeUserDetailsService employeeUserDetailsService;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean JwtAuthenticationFilter jwtFilter;
    @MockitoBean IpWhitelistFilter ipFilter;

    @BeforeEach
    void passInfrastructureFilters() throws Exception {
        when(cakeMovementService.getProductionStatus(any())).thenReturn(new com.lois.management.dto.reservation.ProductionStatus(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()));
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
        org.assertj.core.api.Assertions.assertThat(html).contains("reservation-flavor ellipsis", "가나슈", "/api/reservations/pickup/7", "/api/reservations/pickup-cancel/7", state.equals("PICKED_UP") ? "픽업 완료" : "제작 필요")
                .doesNotContain("pickup-toggle", "픽업 불가", "produce-toggle", "make-toggle", "flavor-btn", "케이크를 만들었나요?");
    }

    @Test
    void onSitePageTargetsNewSaleApiWithCsrf() throws Exception {
        com.lois.management.domain.Cake cake = new com.lois.management.domain.Cake();
        cake.setId(1L);
        cake.setFlavor("가나슈");
        when(reservationService.findAllCakeFlavor()).thenReturn(List.of(cake));
        String html = mockMvc.perform(get("/reservations/on-site").with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("cake/reservation/on-site"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(html).contains("action=\"/cake-stocks/on-site-sales\"",
                "name=\"cake_id\"", "name=\"cake_size\"", "/js/auth/auth-common.js",
                "/js/cake/on-site-sale.js", "name=\"_csrf\"", "data-sale-feedback")
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
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "/reservations,0,0,1,0", "/reservations,1,1,0,0", "/reservations,2,1,0,1",
        "/reservations/production-status,0,0,1,0", "/reservations/production-status,2,1,0,1"
    })
    void sizeTwoUsesStockWhileSizeOneAndProducedStayLegacy(String uri, int stock, int ready, int required, int extra) throws Exception {
        var old = java.util.Map.of(1, java.util.Map.of(3L, 9), 2, java.util.Map.of(3L, 99));
        var produced = java.util.Map.of(1, java.util.Map.of(3L, 4), 2, java.util.Map.of(3L, 7));
        when(cakeMovementService.getProductionStatus(any())).thenReturn(new com.lois.management.dto.reservation.ProductionStatus(old, produced, old, old, old));
        Reservation reserved = new Reservation(); reserved.setCakeId(3L); reserved.setCakeSize(2); reserved.setStatus("RESERVED");
        reserved.setId(1L); reserved.setResDate(java.time.LocalDate.now()); reserved.setResTime(java.time.LocalTime.NOON);
        Reservation picked = new Reservation(); picked.setCakeId(3L); picked.setCakeSize(2); picked.setStatus("PICKED_UP");
        picked.setId(2L); picked.setResDate(java.time.LocalDate.now()); picked.setResTime(java.time.LocalTime.NOON);
        Reservation cancelled = new Reservation(); cancelled.setCakeId(3L); cancelled.setCakeSize(2); cancelled.setStatus("CANCELLED");
        cancelled.setId(3L); cancelled.setResDate(java.time.LocalDate.now()); cancelled.setResTime(java.time.LocalTime.NOON);
        when(reservationService.findTodayOrderByPickUpTime(any())).thenReturn(List.of(reserved, picked, cancelled));
        var need = new com.lois.management.dto.production.CakeProductionNeedDto();
        need.setCakeId(3L); need.setCakeSize(2); need.setReservationQuantity(1); need.setStockQuantity(stock); need.setRequiredQuantity(required);
        when(cakeProductionService.findTodayProductionNeeds()).thenReturn(List.of(need));
        var result = mockMvc.perform(get(uri).with(user("staff").roles("STAFF"))).andExpect(status().isOk()).andReturn();
        var actual = (com.lois.management.dto.reservation.ProductionStatus) result.getModelAndView().getModel().get("status");
        org.assertj.core.api.Assertions.assertThat(actual.getDemand().get(2)).containsEntry(3L, 2);
        org.assertj.core.api.Assertions.assertThat(actual.getPickupReady().get(2)).containsEntry(3L, ready);
        org.assertj.core.api.Assertions.assertThat(actual.getToMake().get(2)).containsEntry(3L, required);
        org.assertj.core.api.Assertions.assertThat(actual.getExtraStock().get(2)).containsEntry(3L, extra);
        org.assertj.core.api.Assertions.assertThat(actual.getDemand().get(1)).isEqualTo(old.get(1));
        org.assertj.core.api.Assertions.assertThat(actual.getPickupReady().get(1)).isEqualTo(old.get(1));
        org.assertj.core.api.Assertions.assertThat(actual.getToMake().get(1)).isEqualTo(old.get(1));
        org.assertj.core.api.Assertions.assertThat(actual.getExtraStock().get(1)).isEqualTo(old.get(1));
        org.assertj.core.api.Assertions.assertThat(actual.getProduced()).isEqualTo(produced);
    }

    @Test
    void searchingLateReservationDoesNotReallocateEarlierReservationsStock() throws Exception {
        var early = new Reservation(); early.setId(1L); early.setCakeId(3L); early.setCakeSize(2);
        early.setStatus("RESERVED"); early.setResTime(java.time.LocalTime.of(11, 29));
        var late = new Reservation(); late.setId(2L); late.setCakeId(3L); late.setCakeSize(2);
        late.setStatus("RESERVED"); late.setResTime(java.time.LocalTime.of(14, 53)); late.setResDate(java.time.LocalDate.now());
        when(reservationService.findTodayOrderByPickUpTime(any())).thenReturn(List.of(late, early));
        when(reservationService.findByContactSuffix("0056")).thenReturn(List.of(late));
        var need = new com.lois.management.dto.production.CakeProductionNeedDto();
        need.setCakeId(3L); need.setCakeSize(2); need.setStockQuantity(1);
        when(cakeProductionService.findTodayProductionNeeds()).thenReturn(List.of(need));
        var result = mockMvc.perform(get("/reservations/search").param("contactSuffix", "0056").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getModelAndView().getModel().get("productionReadyIds")).isEqualTo(List.of(1L));
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("제작 필요").doesNotContain("제작 완료");
    }

    @Test
    void searchDisplaysStockAllocatedReservationAsProductionComplete() throws Exception {
        var row = new Reservation(); row.setId(1L); row.setCakeId(3L); row.setCakeSize(2); row.setStatus("RESERVED");
        row.setResTime(java.time.LocalTime.NOON); row.setResDate(java.time.LocalDate.now());
        when(reservationService.findTodayOrderByPickUpTime(any())).thenReturn(List.of(row));
        when(reservationService.findByContactSuffix("0056")).thenReturn(List.of(row));
        var need = new com.lois.management.dto.production.CakeProductionNeedDto();
        need.setCakeId(3L); need.setCakeSize(2); need.setStockQuantity(1);
        when(cakeProductionService.findTodayProductionNeeds()).thenReturn(List.of(need));
        mockMvc.perform(get("/reservations/search").param("contactSuffix", "0056").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("제작 완료"), org.hamcrest.Matchers.containsString("production-ready-btn"))));
    }

    @Test
    void dashboardRendersClosedStockPanelAfterAllFilterWithoutExtraStockQuery() throws Exception {
        var result = mockMvc.perform(get("/reservations").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(html).contains(
                "/js/auth/auth-common.js", "/js/cake/reservation/reservation-stock-panel.js", "data-stock-panel-toggle",
                "aria-expanded=\"false\"", "aria-controls=\"reservation-stock-panel\"",
                "data-needs-url=\"/cake-production/production-needs\"",
                "data-produce-url=\"/cake-stocks/produce\"", "data-cancel-url=\"/cake-stocks/produce-cancel\"");
        org.assertj.core.api.Assertions.assertThat(html.split("/js/cake/reservation/reservation-stock-panel.js", -1)).hasSize(2);
        int toggle = html.indexOf("data-stock-panel-toggle");
        org.assertj.core.api.Assertions.assertThat(toggle).isGreaterThan(html.indexOf("data-range=\"all\""));
        String section = html.substring(html.indexOf("<section id=\"reservation-stock-panel\""), html.indexOf("<div class=\"reservation-dashboard-layout\""));
        org.assertj.core.api.Assertions.assertThat(section).contains("hidden", "제작현황", "2호");
        // 최초 조회에서는 기존 왼쪽 카드에 필요한 한 번의 조회만 수행한다.
        verify(cakeProductionService, times(1)).findTodayProductionNeeds();
    }

}
