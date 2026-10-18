package com.lois.management.controller;

import com.lois.management.domain.Reservation;
import com.lois.management.domain.Cake;
import com.lois.management.dto.reservation.ReservationPageReq;
import com.lois.management.dto.reservation.ProductionStatus;
import com.lois.management.service.CakeMovementService;
import com.lois.management.service.CakeService;
import com.lois.management.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationListUiTest {
    private final ReservationService service = mock(ReservationService.class);
    private final CakeMovementService movements = mock(CakeMovementService.class);
    private final CakeService cakes = mock(CakeService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        engine.addDialect(new SpringSecurityDialect());
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = new StandaloneMockMvcBuilder(new ReservationController(
                service, movements, cakes)) {
            @Override
            protected Map<String, Object> extendMvcSingletons(jakarta.servlet.ServletContext servletContext) {
                return Map.of("webSecurityExpressionHandler",
                        new org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler());
            }
        }
                .setViewResolvers(views).build();

        Reservation reservation = new Reservation();
        reservation.setId(7L);
        reservation.setResDate(LocalDate.now());
        reservation.setResTime(LocalTime.of(15, 30));
        reservation.setContact("010-1234-0056");
        reservation.setContactSuffix("0056");
        reservation.setCakeSize(2);
        reservation.setPaid(true);
        reservation.setPickupStatus("WAITING");
        when(service.findAll(any(ReservationPageReq.class))).thenReturn(List.of(reservation));
        when(service.findTodayOrderByPickUpTime(any())).thenReturn(List.of(reservation));
        when(service.findFromTodayOrderByPickUpTime()).thenReturn(List.of(reservation));
        when(service.findByDateOrderByPickUpTime(any())).thenReturn(List.of(reservation));
        when(service.findByContactSuffix(anyString())).thenReturn(List.of(reservation));
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "from-today", "date", "today"})
    void listRefreshKeepsHeaderAndRowsUnderSameDateCondition(String range) throws Exception {
        String html = mvc.perform(get("/reservations/list").param("range", range)
                        .param("date", LocalDate.now().toString()).header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertList(html);
        if (range.equals("today")) {
            assertThat(html).contains("reservation-list--today");
        } else {
            assertThat(html).doesNotContain("reservation-list--today");
        }
    }

    @Test
    void dashboardKeepsPartialTargetsInsidePersistentLayoutWrappers() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("test", "test", "ROLE_USER"));
        try {
            Cake cake = new Cake();
            cake.setId(1L);
            cake.setName("가나슈");
            cake.setCode("GANACHE");
            when(cakes.findFlavorsForDashboard()).thenReturn(List.of(cake));
            when(movements.getProductionStatus(any())).thenReturn(new ProductionStatus(
                    Map.of(2, Map.of(1L, 1)), Map.of(), Map.of(), Map.of(), Map.of()));
            String html = mvc.perform(get("/reservations").session(new MockHttpSession())
                            .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("wrap reservation-dashboard", "reservation-dashboard-layout",
                    "reservation-dashboard-production", "reservation-dashboard-reservations",
                    "hx-get=\"/reservations/production-status\"", "id=\"list\"", "id=\"row-block-7\"");
            assertThat(html).contains("reservation-list--today", "data-range=\"today\" aria-pressed=\"true\"");
            verify(service).findTodayOrderByPickUpTime(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
            verify(service, never()).findAll(any(ReservationPageReq.class));
            assertThat(html.indexOf("data-range=\"today\"")).isLessThan(html.indexOf("data-range=\"from-today\""));
            assertThat(html.indexOf("data-range=\"from-today\"")).isLessThan(html.indexOf("data-range=\"date\""));
            assertThat(html.indexOf("data-range=\"date\"")).isLessThan(html.indexOf("data-range=\"all\""));
            assertThat(html.indexOf("class=\"list-header")).isLessThan(html.indexOf("class=\"reservation-dashboard-layout"));
            // 실제 Thymeleaf 렌더링 결과를 브라우저 레이아웃 검증에 재사용한다.
            Path preview = Path.of("build/layout-verification/dashboard.html");
            Files.createDirectories(preview.getParent());
            Files.writeString(preview, html);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void searchShowsDateAndRetainsDetailActions() throws Exception {
        String html = mvc.perform(get("/reservations/search").param("contactSuffix", "0056"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertList(html);
        assertThat(html).doesNotContain("reservation-list--today");
    }

    private void assertList(String html) {
        int summaryStart = html.indexOf("class=\"row summary-row");
        int detailStart = html.indexOf("class=\"detail-row hidden\"");
        String summary = html.substring(summaryStart, detailStart);
        String detail = html.substring(detailStart);
        assertThat(html).contains("class=\"thead\"", "뒤 4자리", "toggleDetailRow(event, 7)");
        String header = html.substring(html.indexOf("class=\"thead\""), summaryStart);
        assertThat(header.indexOf("뒤 4자리")).isGreaterThan(header.indexOf("픽업"));
        assertThat(summary.indexOf("class=\"phone\"")).isGreaterThan(summary.lastIndexOf("pickup-cell"));
        assertThat(summary).contains("class=\"row-no\"", ">2호</div>");
        assertThat(summary).contains("class=\"reservation-date\"", ">0056</div>",
                        "/cake-movements/7/produce-toggle?rowNo=1", "/api/reservations/7/pickup-toggle?rowNo=1")
                .doesNotContain("010-1234-0056", "openConfirmModal", "/7/edit");
        assertThat(detail).contains("예약 날짜:", "010-1234-0056", "/reservations/7/edit", "openConfirmModal");
    }
}
