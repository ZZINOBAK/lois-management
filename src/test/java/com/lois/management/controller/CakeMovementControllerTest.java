package com.lois.management.controller;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.service.CakeMovementService;
import com.lois.management.service.CakeService;
import com.lois.management.service.CakeProductionService;
import com.lois.management.dto.production.CakeProductionNeedDto;
import com.lois.management.service.ReservationService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CakeMovementControllerTest {
    private final ReservationService reservations = mock(ReservationService.class);
    private final CakeMovementService movements = mock(CakeMovementService.class);
    private final CakeProductionService production = mock(CakeProductionService.class);
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
        mvc = new StandaloneMockMvcBuilder(new CakeMovementController(reservations, movements, mock(CakeService.class), production)) {
            @Override
            protected Map<String, Object> extendMvcSingletons(jakarta.servlet.ServletContext context) {
                return Map.of("webSecurityExpressionHandler",
                        new org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler());
            }
        }.setViewResolvers(views).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("staff", "test", "ROLE_STAFF"));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void productionPageRendersActualIdsCodesAndIsolatedAssets() throws Exception {
        when(reservations.findAllCakeFlavor()).thenReturn(List.of(
                cake(11L, "가나슈", "GANACHE"), cake(12L, "무화과", "FIG"),
                cake(13L, "모카", "MOKA"), cake(14L, "레몬", "LEMON"), cake(15L, "바닐라", "VANILLA")));
        when(reservations.findTodayOrderByPickUpTime(any())).thenReturn(List.of(
                reservation(701L, LocalTime.of(12, 0), "가나슈"),
                reservation(702L, LocalTime.of(13, 30), "무화과"),
                reservation(703L, LocalTime.of(14, 0), "레몬")));
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 2), need("모카", 0)));
        String html = render();
        for (long id = 11; id <= 15; id++) {
            assertThat(html).contains("data-cake-id=\"" + id + "\"");
        }
        assertThat(html).contains("data-flavor-code=\"FIG\"", "data-flavor-code=\"VANILLA\"",
                "data-produce-url=\"/cake-stocks/produce\"", "data-production-action=\"other\"",
                "/css/cake-production.css", "/js/cake-production.js", "/js/auth-common.js",
                "content=\"test-token\"", "content=\"X-CSRF-TOKEN\"", "role=\"status\"");
        assertThat(html).doesNotContain("type=\"radio\"", "<textarea", "type=\"submit\" class=\"btn",
                "/css/on-site.css", "/css/index.css", "/css/reservation-dashboard.css", "htmx", "auth-jwt.js",
                "action=\"/cake-movements/produce\"");
        assertThat(html).contains("data-cancel-url=\"/cake-stocks/produce-cancel\"",
                "aria-label=\"가나슈 2호 1개 제작 취소\"");
        assertThat(html.split("data-production-action=\"cancel\"", -1)).hasSize(6);
        assertThat(html).doesNotContain("aria-label=\"기타 2호 1개 제작 취소\"");
        verify(reservations).findAllCakeFlavor();
        verifyNoInteractions(movements);
        Path preview = Path.of("build/reports/cake-production-preview.html");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, html);
    }

    @Test
    void tableEntriesRemainVisibleAndExistingOtherDoesNotDuplicate() throws Exception {
        when(reservations.findAllCakeFlavor()).thenReturn(List.of(
                cake(20L, "딸기", "STRAWBERRY"), cake(30L, "기타", "CUSTOM")));
        String html = render();
        assertThat(html).contains("data-cake-id=\"20\"", "data-flavor=\"딸기\"");
        assertThat(html.split("data-production-action=\"other\"", -1)).hasSize(2);
        assertThat(html.split("data-production-action=\"cancel\"", -1)).hasSize(2);
        assertThat(html).doesNotContain("aria-label=\"기타 2호 1개 제작 취소\"");
    }

    @Test
    void existingFlavorQueryReturnsCodeWithoutGroupingRows() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lois.management.domain");
        try (InputStream input = getClass().getResourceAsStream("/mapper/ReservationMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "ReservationMapper.xml", configuration.getSqlFragments()).parse();
        }
        String sql = configuration.getMappedStatement(ReservationMapper.class.getName() + ".findAllCakeFlavor")
                .getBoundSql(null).getSql();
        assertThat(sql).contains("id, flavor, flavor_code AS code", "FROM cakes", "ORDER BY id ASC")
                .doesNotContain("GROUP BY", "MIN(");
    }

    @Test
    void productionPageUsesSameTodayQueryAndShowsOnlySummaryColumns() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Reservation first = reservation(501L, LocalTime.of(9, 0), "가나슈");
        first.setRowNumber(51);
        first.setContact("010-1234-9876");
        first.setCakeSize(1);
        first.setPaid(true);
        first.setStatus("PICKED_UP");
        first.setPickupStatus("WAITING");
        first.setMakeStatus("RESERVED");
        Reservation second = reservation(502L, LocalTime.of(13, 30), "무화과");
        second.setRowNumber(52);
        second.setPickupStatus("PICKED");
        String flavor = "<script>alert(1)</script>";
        when(reservations.findTodayOrderByPickUpTime(today)).thenReturn(List.of(
                first, second, reservation(503L, LocalTime.of(14, 5), flavor)));

        String html = render();
        String table = html.substring(html.indexOf("<table"), html.indexOf("</table>"));

        verify(reservations).findTodayOrderByPickUpTime(today);
        assertThat(table).contains(">연번</th>", ">시간</th>", ">맛</th>", ">상태</th>", "제작 필요", "픽업 완료", "is-production-needed",
                ">1</td>", ">2</td>", ">3</td>", "09시", "13시 30분", "14시 05분",
                "&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(table.indexOf("가나슈")).isLessThan(table.indexOf("무화과"));
        assertThat(table).doesNotContain("00분", ">09:00</td>", ">13:30</td>", ">51</td>", ">52</td>",
                "010-1234-9876", "호수", "결제", "PICKED_UP", "<button", "<script>");
        assertThat(table).contains("data-reservation-id=\"501\"", "data-make-status=\"RESERVED\"",
                "data-pickup-status=\"WAITING\"", "data-cake-size=\"1\"", "data-pickup-time=\"09:00\"");
        verifyNoInteractions(movements);
    }

    @Test
    void productionPageShowsEmptyReservationMessage() throws Exception {
        when(reservations.findTodayOrderByPickUpTime(any())).thenReturn(List.of());
        assertThat(render()).contains("오늘 예약이 없습니다.", "colspan=\"4\"");
    }

    @Test
    void productionNeedsEndpointReturnsQuantityDtoWithoutCaching() throws Exception {
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 2), need("모카", 0)));
        mvc.perform(get("/cake-movements/production-needs"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].cakeFlavor").value("가나슈"))
                .andExpect(jsonPath("$[0].cakeSize").value(2))
                .andExpect(jsonPath("$[0].requiredQuantity").value(2))
                .andExpect(jsonPath("$[1].requiredQuantity").value(0));
        verify(production).findTodayProductionNeeds();
        verifyNoInteractions(reservations, movements);
    }

    @Test
    void productionNeedsInitiallyRendersZeroNormallyAndPositiveEmphasized() throws Exception {
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 2), need("모카", 0)));
        String html = render();
        String section = html.substring(html.indexOf("<section class=\"cake-production-needs\""), html.indexOf("</section>"));
        assertThat(section).contains("가나슈", "모카", "is-needed", ">2</span>", ">0</span>");
        assertThat(section.split("is-needed", -1)).hasSize(2);
        assertThat(html).contains("data-needs-url=\"/cake-movements/production-needs\"",
                "data-production-need", "data-cake-size=\"2\"", "data-stock-quantity=\"0\"");
        verify(production).findTodayProductionNeeds();
    }

    private CakeProductionNeedDto need(String flavor, int required) {
        CakeProductionNeedDto need = new CakeProductionNeedDto();
        need.setCakeId("가나슈".equals(flavor) ? 1L : 2L);
        need.setCakeFlavor(flavor);
        need.setCakeSize(2);
        need.setRequiredQuantity(required);
        need.setStockQuantity("가나슈".equals(flavor) ? 1 : 0);
        return need;
    }

    private Reservation reservation(Long id, LocalTime time, String flavor) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setResTime(time);
        reservation.setCakeFlavor(flavor);
        reservation.setCakeId("가나슈".equals(flavor) ? 1L : 2L);
        reservation.setCakeSize(2);
        reservation.setMakeStatus("RESERVED");
        reservation.setPickupStatus("WAITING");
        return reservation;
    }

    private String render() throws Exception {
        return mvc.perform(get("/cake-movements/produce")
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk()).andExpect(view().name("cake-movement/produce"))
                .andReturn().getResponse().getContentAsString();
    }

    private Cake cake(Long id, String flavor, String code) {
        Cake cake = new Cake();
        cake.setId(id);
        cake.setFlavor(flavor);
        cake.setCode(code);
        return cake;
    }
}
