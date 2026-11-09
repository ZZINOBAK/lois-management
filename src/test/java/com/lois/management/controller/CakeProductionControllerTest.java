package com.lois.management.controller;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.lois.management.auth.SecurityConfig;
import com.lois.management.auth.JwtAuthenticationFilter;
import com.lois.management.config.filter.IpWhitelistFilter;
import com.lois.management.auth.EmployeeUserDetailsService;
import com.lois.management.auth.LoginSuccessHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.service.CakeProductionService;
import com.lois.management.dto.production.CakeProductionNeedDto;
import com.lois.management.service.ReservationService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CakeProductionController.class)
@Import(SecurityConfig.class)
class CakeProductionControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    ReservationService reservations;
    @MockitoBean
    CakeProductionService production;
    @MockitoBean
    JwtAuthenticationFilter jwtFilter;
    @MockitoBean
    IpWhitelistFilter ipFilter;
    @MockitoBean
    EmployeeUserDetailsService users;
    @MockitoBean
    LoginSuccessHandler login;

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
    void productionPageRendersActualIdsCodesAndIsolatedAssets() throws Exception {
        when(reservations.findAllCakeFlavor()).thenReturn(List.of(
                cake(11L, "가나슈", "GANACHE"), cake(12L, "무화과", "FIG"),
                cake(13L, "모카", "MOKA"), cake(14L, "레몬", "LEMON"), cake(15L, "바닐라", "VANILLA")));
        when(reservations.findTodayOrderByPickUpTime(any())).thenReturn(List.of(
                reservation(701L, LocalTime.of(12, 0), "가나슈"),
                reservation(702L, LocalTime.of(13, 30), "무화과"),
                reservation(703L, LocalTime.of(14, 0), "레몬")));
        CakeProductionNeedDto ganache = need("가나슈", 2);
        ganache.setCakeId(11L);
        CakeProductionNeedDto moka = need("모카", 0);
        moka.setCakeId(13L);
        when(production.findTodayProductionNeeds()).thenReturn(List.of(ganache, moka));
        String html = render();
        for (long id = 11; id <= 15; id++) {
            assertThat(html).contains("data-cake-id=\"" + id + "\"");
        }
        assertThat(html).contains("data-flavor-code=\"FIG\"", "data-flavor-code=\"VANILLA\"",
                "data-produce-url=\"/cake-stocks/produce\"", "data-production-action=\"other\"",
                "/css/cake/cake-production.css", "/js/cake/cake-production.js", "/js/auth/auth-common.js",
                "content=\"X-CSRF-TOKEN\"", "role=\"status\"");
        assertThat(html).containsPattern("name=\"_csrf\" content=\"[^\"]+\"");
        assertThat(html).doesNotContain("type=\"radio\"", "<textarea", "type=\"submit\" class=\"btn",
                "/css/cake/on-site.css", "/css/index.css", "/css/cake/reservation-dashboard.css", "htmx", "/auth/auth-jwt.js",
                "action=\"/cake-movements/produce\"");
        assertThat(html).contains("data-cancel-url=\"/cake-stocks/produce-cancel\"",
                "aria-label=\"가나슈 2호 1개 제작 취소\"");
        assertThat(html.split("data-production-action=\"cancel\"", -1)).hasSize(6);
        assertThat(html).doesNotContain("aria-label=\"기타 2호 1개 제작 취소\"");
        verify(reservations).findAllCakeFlavor();
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
                "010-1234-9876", "결제", "<button", "<script>");
        assertThat(table).contains("data-reservation-id=\"501\"", "data-status=\"PICKED_UP\"", "data-cake-size=\"1\"", "data-pickup-time=\"09:00\"");
        assertThat(table).doesNotContain("data-make-status", "data-pickup-status");
    }

    @Test
    void productionPageShowsEmptyReservationMessage() throws Exception {
        when(reservations.findTodayOrderByPickUpTime(any())).thenReturn(List.of());
        assertThat(render()).contains("오늘 예약이 없습니다.", "colspan=\"5\"");
    }

    @Test
    void productionNeedsEndpointReturnsQuantityDtoWithoutCaching() throws Exception {
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 2), need("모카", 0)));
        mvc.perform(get("/cake-production/production-needs").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].cakeFlavor").value("가나슈"))
                .andExpect(jsonPath("$[0].cakeSize").value(2))
                .andExpect(jsonPath("$[0].requiredQuantity").value(2))
                .andExpect(jsonPath("$[1].requiredQuantity").value(0));
        verify(production).findTodayProductionNeeds();
        verifyNoInteractions(reservations);
    }

    @Test
    void productionNeedsInitiallyRendersZeroNormallyAndPositiveEmphasized() throws Exception {
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 2), need("모카", 0)));
        String html = render();
        String section = html.substring(html.indexOf("<section class=\"cake-production-needs\""), html.indexOf("</section>"));
        assertThat(section).contains("가나슈", "모카", "is-needed", ">2</span>", ">0</span>");
        assertThat(section.split("is-needed", -1)).hasSize(2);
        assertThat(html).contains("data-needs-url=\"/cake-production/production-needs\"",
                "data-production-need", "data-cake-size=\"2\"", "data-stock-quantity=\"0\"");
        verify(production).findTodayProductionNeeds();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cake-production", "/cake-production/production-needs"})
    void otherRoleCannotReadProduction(String path) throws Exception {
        mvc.perform(get(path).with(user("other").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservations, production);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/cake-production", "/cake-production/production-needs"})
    void anonymousCannotReadProduction(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        verifyNoInteractions(reservations, production);
    }

    @Test
    void adminCanReadProductionNeeds() throws Exception {
        when(production.findTodayProductionNeeds()).thenReturn(List.of());
        mvc.perform(get("/cake-production/production-needs")
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void cardsShowCurrentStockAndZeroWithoutSizeOrIncrementLabels() throws Exception {
        when(reservations.findAllCakeFlavor()).thenReturn(List.of(
                cake(1L, "가나슈", "GANACHE"), cake(2L, "모카", "MOKA"), cake(3L, "기타", "CUSTOM")));
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 7), need("모카", 9)));
        String html = render();
        String cards = html.substring(html.indexOf("class=\"cake-production-grid\""), html.indexOf("<aside"));
        assertThat(cards).contains("aria-label=\"가나슈 현재 수량\"", "aria-label=\"모카 현재 수량\"",
                ">1</span>", ">0</span>");
        assertThat(cards.split("data-card-stock", -1)).hasSize(3);
        assertThat(cards).doesNotContain("2호 · +1", ">7</span>", ">9</span>", "aria-label=\"기타 현재 수량\"");
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

    @Test
    void sizeOneNeedsRenderSeparatelyAndBadgeAppearsOnlyForSizeOne() throws Exception {
        var one = need("무화과", 1);
        one.setCakeSize(1);
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 0), one));
        var reservation = reservation(1L, LocalTime.NOON, "무화과");
        reservation.setCakeSize(1);
        when(reservations.findTodayOrderByPickUpTime(any())).thenReturn(List.of(reservation));
        String html = render();
        String group = html.substring(html.indexOf("<div class=\"cake-production-needs-one\""), html.indexOf("</section>"));
        assertThat(group).contains("무화과", "data-cake-size=\"1\"").doesNotContain("가나슈", "hidden");
        assertThat(html).contains("class=\"cake-production-size-badge\">1호</span>");
    }

    @Test
    void sizeOneGroupIsHiddenWithoutSizeOneReservations() throws Exception {
        when(production.findTodayProductionNeeds()).thenReturn(List.of(need("가나슈", 0)));
        String html = render();
        String group = html.substring(html.indexOf("<div class=\"cake-production-needs-one\""), html.indexOf("</section>"));
        assertThat(group).contains("hidden");
    }

    private Reservation reservation(Long id, LocalTime time, String flavor) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setResTime(time);
        reservation.setCakeFlavor(flavor);
        reservation.setCakeId("가나슈".equals(flavor) ? 1L : 2L);
        reservation.setCakeSize(2);
        reservation.setStatus("RESERVED");
        reservation.setMakeStatus("RESERVED");
        reservation.setPickupStatus("WAITING");
        return reservation;
    }

    private String render() throws Exception {
        return mvc.perform(get("/cake-production").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(view().name("cake/production/production-management"))
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
