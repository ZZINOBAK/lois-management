package com.lois.management.service;

import com.lois.management.domain.Reservation;
import com.lois.management.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationMapper reservationMapper;

    public void create(Reservation reservation) {
        reservationMapper.insert(reservation);
    }
}
