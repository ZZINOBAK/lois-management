package com.lois.management.controller;

import com.lois.management.domain.Reservation;
import com.lois.management.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(ReservationController.class)   // Controller만 로딩해서 웹 계층 테스트
class ReservationControllerTest {
    @Autowired
    MockMvc mockMvc;        // HTTP 요청 흉내 내 주는 친구

    @MockBean
    ReservationService reservationService;   // 컨트롤러가 의존하는 서비스는 Mock으로 대체


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
