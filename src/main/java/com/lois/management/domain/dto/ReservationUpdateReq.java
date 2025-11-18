package com.lois.management.domain.dto;

public record ReservationUpdateReq(
        String resDate,
        String resTime,
        String contact,
        Boolean paid,
        String status,
        String note
) { }
