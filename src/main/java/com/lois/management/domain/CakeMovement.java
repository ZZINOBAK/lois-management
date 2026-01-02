package com.lois.management.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CakeMovement {
    private Long id;
    private LocalDate bizDate;
    private Long cakeId;
    private Integer cakeSize;

    private Integer delta;          // +1 / -1
    private String moveType;        // PRODUCED / PICKUP / ON_SITE / ADJUST

    private Long reservationId;     // nullable
    private String requestId;       // UUID
    private Long reversedOfId;      // nullable
    private String memo;            // nullable
    private LocalDateTime createdAt;

    //DemandCountRow
    private Integer cnt;   // demand or stock

    //StockCountRow
    private Integer stock; // SUM(delta)


}
