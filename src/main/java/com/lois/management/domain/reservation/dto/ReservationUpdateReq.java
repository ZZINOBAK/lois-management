package com.lois.management.domain.reservation.dto;

public record ReservationUpdateReq(
        CharSequence resDate,
        CharSequence resTime,
        String contact,
        Boolean paid,
        String status,
        String note
) {}
