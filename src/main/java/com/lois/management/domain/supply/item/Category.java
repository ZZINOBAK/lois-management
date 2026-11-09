package com.lois.management.domain.supply.item;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {
    private Long id;
    private String categoryName;
    private String note;
    private LocalDateTime createAt;
    private LocalDateTime updatedAt;
}
