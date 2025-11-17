package com.lois.management.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReservationCreateReq(
        @NotNull Long cakeId,
        @NotBlank String resDate,   // "2025-11-15"
        @NotBlank String resTime,   // "15:30:00"
        @Pattern(regexp = "^[0-9\\-]{9,20}$") String contact,
        Boolean paid
) { }
