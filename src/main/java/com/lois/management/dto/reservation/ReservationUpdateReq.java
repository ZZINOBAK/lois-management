package com.lois.management.dto.reservation;

public record ReservationUpdateReq(
        CharSequence resDate,
        CharSequence resTime,
        String contact,
        Boolean paid,
        String status,
        String note
) {}
