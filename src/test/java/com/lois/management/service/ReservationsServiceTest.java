package com.lois.management.service;

import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationCreateReq;
import com.lois.management.dto.reservation.ReservationPageReq;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.ReservationSlotAcquisitionSupport;
import com.lois.management.service.reservation.limiter.ReservationLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)   // Mockito랑 JUnit5 연결
class ReservationsServiceTest {

    @Mock
    ReservationMapper reservationMapper;   // 가짜 Mapper
    @Mock
    ReservationPolicyMapper reservationPolicyMapper;
    @Mock
    ReservationLimiter reservationConcurrencyGuard;

    @InjectMocks
    ReservationService reservationService; // 위 가짜 mapper를 주입받은 진짜 서비스

    @Test
    void create_요청_DTO를_엔티티로_변환해서_insert까지_호출한다() {
        // given
        ReservationCreateReq req = new ReservationCreateReq(
                1L,                  // cakeId
                "2026-11-20",        // resDate
                "15:30",             // resTime
                "010-1234-5678",     // contact
                true                 // paid
        );

        when(reservationConcurrencyGuard.isCreationAllowed()).thenReturn(true);
        when(reservationConcurrencyGuard.tryAcquireSlot(any(), any()))
                .thenReturn(ReservationSlotAcquisitionSupport.acquired(
                        LocalDate.parse("2026-11-20"), LocalTime.parse("15:30")));
        when(reservationMapper.insert(any())).thenReturn(1);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);

        reservationService.create(req);

        verify(reservationMapper, times(1)).insert(captor.capture());

        Reservation saved = captor.getValue();
        assertThat(saved.getCakeId()).isEqualTo(1L);
        assertThat(saved.getResDate()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(saved.getResTime()).isEqualTo(LocalTime.parse("15:30"));
        assertThat(saved.getContact()).isEqualTo("010-1234-5678");
        assertThat(saved.getPaid()).isTrue();

        // 폰 뒷자리 컬럼 로직 체크
        assertThat(saved.getContactSuffix()).isEqualTo("5678");
    }



}
