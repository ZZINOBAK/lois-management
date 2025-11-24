package com.lois.management.dto.reservation;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ReservationRes {
    private final Long id;
    private final String status;


}
