package com.lois.management.domain.supply.item;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Item {
    private Long id;
    private String itemName;
    private int currentQty;
    private int minQty;
    private Long categoryId;
    private String note;
    private LocalDateTime createAt;
    private LocalDateTime updatedAt;
}
