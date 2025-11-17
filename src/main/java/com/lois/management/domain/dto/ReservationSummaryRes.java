package com.lois.management.domain.dto;

public record ReservationSummaryRes(
        Long id, Long cakeId, String resDate, String resTime, String status
) {
}
