package com.lois.management.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationPolicy {
    private Long id;
    private int dailyMaxLimit;
    private int hourlyMaxLimit;
    private LocalDateTime updatedAt;
}
