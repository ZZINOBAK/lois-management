package com.lois.management.controller.demo;

import com.lois.management.service.demo.DemoDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({
        DemoController.class,
        DemoReservationController.class,
        DemoStockRequestController.class,
        DemoItemController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(DemoDataService.class)
class DemoReservationControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    com.lois.management.auth.JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    com.lois.management.auth.EmployeeUserDetailsService employeeUserDetailsService;

    @MockitoBean
    com.lois.management.auth.LoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    com.lois.management.service.IpAccessService ipAccessService;

    @Test
    void demoReservationDashboardUsesDemoTemplate() throws Exception {
        mockMvc.perform(get("/demo/reservations"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/reservation-dashboard"))
                .andExpect(content().string(containsString("/demo/reservations/list")))
                .andExpect(content().string(containsString("/demo/reservations/produce")))
                .andExpect(content().string(containsString("/demo/reservations/on-site")))
                .andExpect(content().string(containsString("/demo/reservations/simple-reservation")))
                .andExpect(content().string(containsString("/demo/reservations/new")))
                .andExpect(content().string(not(containsString("/demo/stock-requests"))));
    }

    @Test
    void demoGeneralReservationFinishStoresInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        java.time.LocalDate pickupDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1);
        while (pickupDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            pickupDate = pickupDate.plusDays(1);
        }

        mockMvc.perform(get("/demo/reservations/new").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/reserve"));

        mockMvc.perform(post("/demo/reservations/step/1").session(session).param("cakeId", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/steps :: step2"));

        mockMvc.perform(post("/demo/reservations/step/2").session(session)
                        .param("date", pickupDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/steps :: step3"));

        mockMvc.perform(post("/demo/reservations/step/3").session(session).param("time", "15:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/steps :: step4"));

        mockMvc.perform(post("/demo/reservations/step/4").session(session).param("contact", "010-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/steps :: step5"));

        mockMvc.perform(post("/demo/reservations/step/5").session(session).param("paid", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/steps :: step6"));

        mockMvc.perform(post("/demo/reservations/finish").session(session).param("note", "일반예약 메모"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));

        mockMvc.perform(get("/demo/reservations/search").session(session).param("contactSuffix", "5678"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("일반예약 메모")))
                .andExpect(content().string(containsString("딸기")));
    }

    @Test
    void demoProduceAndOnSiteUseSessionOnly() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/demo/reservations/produce").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/produce"))
                .andExpect(content().string(containsString("제작완료")));

        mockMvc.perform(post("/demo/reservations/produce").session(session)
                        .param("cakeId", "2")
                        .param("cakeSize", "2")
                        .param("note", "수동 제작"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));

        mockMvc.perform(get("/demo/reservations/on-site").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("demo/reservation/on-site"));

        mockMvc.perform(post("/demo/reservations/on-site").session(session)
                        .param("cakeId", "2")
                        .param("cakeSize", "2")
                        .param("note", "현장판매"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));
    }

    @Test
    void demoReservationCanCreateToggleAndDeleteInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/demo/reservations/simple-reservation")
                        .session(session)
                        .param("cakeId", "1")
                        .param("cakeSize", "1")
                        .param("pickupTime", LocalTime.of(15, 30).toString())
                        .param("contactSuffix", "2468")
                        .param("paid", "true")
                        .param("note", "테스트 예약"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));

        mockMvc.perform(get("/demo/reservations/search").session(session).param("contactSuffix", "2468"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("테스트 예약")))
                .andExpect(content().string(containsString("/demo/reservations/6/pickup-toggle")))
                .andExpect(content().string(containsString("/demo/reservations/6/make-toggle")));

        mockMvc.perform(patch("/demo/reservations/6/pickup-toggle").session(session).param("rowNo", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("픽업완료")));

        mockMvc.perform(delete("/demo/reservations/6").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("테스트 예약"))));
    }

    @Test
    void demoReservationDataIsIsolatedByHttpSession() throws Exception {
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();

        mockMvc.perform(post("/demo/reservations/simple-reservation")
                        .session(firstSession)
                        .param("cakeId", "2")
                        .param("cakeSize", "2")
                        .param("pickupTime", LocalTime.of(16, 30).toString())
                        .param("contactSuffix", "8642")
                        .param("paid", "false")
                        .param("note", "첫번째 세션 전용 예약"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/reservations"));

        mockMvc.perform(patch("/demo/reservations/6/pickup-toggle")
                        .session(firstSession)
                        .param("rowNo", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("픽업완료")));

        mockMvc.perform(get("/demo/reservations/search")
                        .session(firstSession)
                        .param("contactSuffix", "8642"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("첫번째 세션 전용 예약")))
                .andExpect(content().string(containsString("픽업완료")));

        mockMvc.perform(get("/demo/reservations/search")
                        .session(secondSession)
                        .param("contactSuffix", "8642"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("첫번째 세션 전용 예약"))))
                .andExpect(content().string(not(containsString("픽업완료"))));
    }

    @Test
    void demoStockRequestCreatesWithoutApiRedirect() throws Exception {
        mockMvc.perform(post("/demo/stock-requests").param("itemId", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/demo/stock-requests"));

        mockMvc.perform(get("/demo/stock-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/demo/items?category=전체")))
                .andExpect(content().string(containsString("hx-post=\"/demo/stock-requests\"")));
    }
}
