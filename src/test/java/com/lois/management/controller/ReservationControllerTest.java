package com.lois.management.controller;

import com.lois.management.auth.EmployeeUserDetailsService;
import com.lois.management.auth.JwtTokenProvider;
import com.lois.management.domain.Reservation;
import com.lois.management.service.CakeMovementService;
import com.lois.management.service.CakeService;
import com.lois.management.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Disabled("Security/CSRF/Thymeleaf 적용 후 테스트 환경 재정비 필요")
@WebMvcTest(ReservationController.class)   // Controller만 로딩해서 웹 계층 테스트
@AutoConfigureMockMvc(addFilters = false) // 그런데 지금 테스트 목적은 이거잖아.
// /reservations 요청하면 reservation/dashboard 뷰와 reservations 모델을 반환하는가? 이건 JWT 검증 테스트가 아니니까 필터 끄는 게 더 적절해.
@WithMockUser(username = "testUser")
//@ActiveProfiles("local")
@ActiveProfiles("test")
class ReservationControllerTest {
    @Autowired
    MockMvc mockMvc;        // HTTP 요청 흉내 내 주는 친구

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    ReservationService reservationService;   // 컨트롤러가 의존하는 서비스는 Mock으로 대체
    @MockitoBean
    EmployeeUserDetailsService employeeUserDetailsService;

    @MockitoBean
    CakeMovementService cakeMovementService;
    @MockitoBean
    CakeService cakeService;

    @Test
    void 대시보드_조회시_예약목록과_뷰를_반환한다() throws Exception {
        // given
        Reservation r = new Reservation();
        r.setId(1L);

        given(reservationService.findAll())
                .willReturn(List.of(r));

        // when & then
        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservation/dashboard"))
                .andExpect(model().attributeExists("reservations"))
                .andExpect(model().attribute("reservations", hasSize(1)));

        verify(reservationService).findAll();
    }
}
