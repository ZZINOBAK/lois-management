package com.lois.management.dto.reservation;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.sql.Time;
import java.util.Date;

@Data
@RequiredArgsConstructor
public class ReservationSummaryRes {
    private final Long id;
    private final Long cakeId;
    private final String resDate;
    private final String resTime;
    private final String Status;

}
