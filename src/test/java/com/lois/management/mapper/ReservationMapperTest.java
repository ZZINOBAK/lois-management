package com.lois.management.mapper;

import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationPageReq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ReservationMapperTest {

    @Autowired
    ReservationMapper reservationMapper;

    @Test
    void 케이크_예약_리스트_페이징() {
        //given
        ReservationPageReq reservationPageReq = new ReservationPageReq();
        ReservationPageReq reservationPageReq2 = new ReservationPageReq();
        reservationPageReq2.setPage(2);
        reservationPageReq2.setSize(30);

        //when
        List<Reservation> reservations = reservationMapper.findAll(reservationPageReq);
        List<Reservation> reservations2 = reservationMapper.findAll(reservationPageReq2);

        //then
        assertThat(reservations.size()).isEqualTo(20);
        assertThat(reservations2.size()).isEqualTo(30);
    }

    @Test
    void 케이크_예약_리스트_조회_페이징_네이게이션_추가() {
        //given
        ReservationPageReq reservationPageReq = new ReservationPageReq();
        reservationPageReq.setTotalCount(reservationMapper.countAll());
        reservationPageReq.calculate();

        //when
        List<Reservation> reservations = reservationMapper.findAll(reservationPageReq);

        //then
        assertThat(reservations).hasSize(20);
        assertThat(reservationPageReq.getTotalCount()).isGreaterThan(0);
        assertThat(reservationPageReq.getTotalPage()).isGreaterThan(0);
        assertThat(reservationPageReq.getStartPage()).isEqualTo(1);

    }
}
