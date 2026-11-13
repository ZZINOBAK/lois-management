package com.lois.management.domain.reservation.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationCountDto(
        LocalDate resDate,
        LocalTime resTime,
        int count
) {}