package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.domain.dto.ReservationCreateReq;
import com.lois.management.mapper.ReservationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)   // Mockito랑 JUnit5 연결
class ReservationsServiceTest {

    @Mock
    ReservationMapper reservationMapper;   // 가짜 Mapper

    @InjectMocks
    ReservationService reservationService; // 위 가짜 mapper를 주입받은 진짜 서비스

    @Test
    void create_요청_DTO를_엔티티로_변환해서_insert까지_호출한다() {
        // given
        ReservationCreateReq req = new ReservationCreateReq(
                1L,                  // cakeId
                "2025-11-20",        // resDate
                "15:30",             // resTime
                "010-1234-5678",     // contact
                true                 // paid
        );

        // insert가 void라서, 여기서는 따로 when().thenReturn()은 안 써도 됨

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);

        // when
        reservationService.create(req);  // 이 메서드를 호출하면 내부에서 mapper.insert()가 불려야 함

        // then
        // mapper.insert()가 한 번 호출되면서 Reservation 객체를 넘겼는지 확인
        verify(reservationMapper, times(1)).insert(captor.capture());

        Reservation saved = captor.getValue();
        assertThat(saved.getCakeId()).isEqualTo(1L);
        assertThat(saved.getResDate()).isEqualTo(LocalDate.parse("2025-11-20"));
        assertThat(saved.getResTime()).isEqualTo(LocalTime.parse("15:30"));
        assertThat(saved.getContact()).isEqualTo("010-1234-5678");
        assertThat(saved.isPaid()).isTrue();
    }

}
