package com.lois.management.auth;

import com.lois.management.config.filter.IpWhitelistFilter;
import com.lois.management.controller.demo.DemoController;
import com.lois.management.controller.demo.DemoItemController;
import com.lois.management.controller.demo.DemoReservationController;
import com.lois.management.controller.demo.DemoStockRequestController;
import com.lois.management.domain.Reservation;
import com.lois.management.service.demo.DemoDataService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        DemoController.class,
        DemoItemController.class,
        DemoStockRequestController.class,
        DemoReservationController.class
})
@Import({
        SecurityConfig.class,
        DemoDataService.class,
        DemoSecurityConfigTest.PassThroughIpWhitelistFilterConfig.class
})
class DemoSecurityConfigTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    EmployeeUserDetailsService employeeUserDetailsService;

    @MockitoBean
    LoginSuccessHandler loginSuccessHandler;

    @Test
    void demoIsPublicButOperationalPagesStillRequireLogin() throws Exception {
        mockMvc.perform(get("/demo"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/demo/items"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reservations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/stock-requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void demoProduceAndOnSiteFormsIncludeCsrfToken() throws Exception {
        mockMvc.perform(get("/demo/reservations/produce"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/demo/reservations/on-site"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void demoProducePostRequiresCsrfAndSucceedsWithToken() throws Exception {
        mockMvc.perform(post("/demo/reservations/produce")
                        .param("cakeId", "2")
                        .param("cakeSize", "2")
                        .param("note", "csrf-missing"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/demo/reservations/produce")
                        .with(csrf())
                        .param("cakeId", "2")
                        .param("cakeSize", "2")
                        .param("note", "csrf-ok"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));
    }

    @Test
    void demoOnSitePostRequiresCsrfAndSucceedsWithToken() throws Exception {
        mockMvc.perform(post("/demo/reservations/on-site")
                        .param("cakeId", "1")
                        .param("cakeSize", "1")
                        .param("note", "csrf-missing"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/demo/reservations/on-site")
                        .with(csrf())
                        .param("cakeId", "1")
                        .param("cakeSize", "1")
                        .param("note", "csrf-ok"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));
    }

    @Test
    void demoFinishPostRequiresCsrfAndSucceedsWithToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Reservation reserve = new Reservation();
        reserve.setCakeId(3L);
        reserve.setResDate(LocalDate.now(KST).plusDays(2));
        reserve.setResTime(LocalTime.of(15, 0));
        reserve.setContact("010-9999-8888");
        reserve.setPaid(true);
        reserve.setNote("finish csrf");
        session.setAttribute("demoReserve", reserve);

        mockMvc.perform(post("/demo/reservations/finish")
                        .session(session)
                        .param("note", "finish csrf missing"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/demo/reservations/finish")
                        .session(session)
                        .with(csrf())
                        .param("note", "finish csrf ok"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));
    }

    @TestConfiguration
    static class PassThroughIpWhitelistFilterConfig {
        @Bean
        IpWhitelistFilter ipWhitelistFilter() {
            return new IpWhitelistFilter(null, null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response,
                                                FilterChain filterChain)
                        throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }
}
