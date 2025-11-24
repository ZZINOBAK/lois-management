package com.lois.management.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Setter
@Getter
public class Reservation {
    private Long id;
    private LocalDate resDate;
    private LocalTime resTime;
    private Long cakeId;
    private int cakeSize;
    private int candles;
    private String contact;
    private boolean paid;
    private String makeStatus;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime pickedUpAt;
    private String pickupStatus;

    private String cakeFlavor;
    private int rowNumber;
}
