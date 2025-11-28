package com.lois.management.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockRequest {
    private Long id;
    private Long itemId;
    private int reqQty;
    private String status;
    private String note;
    private LocalDateTime lastSmSAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String itemName;
    private Long daysSinceRequest;
}
