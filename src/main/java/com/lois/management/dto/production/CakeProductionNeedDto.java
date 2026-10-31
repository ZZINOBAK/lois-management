package com.lois.management.dto.production;

import lombok.Data;

@Data
public class CakeProductionNeedDto {
    private Long cakeId;
    private String cakeFlavor;
    private Integer cakeSize;
    private int reservationQuantity;
    private int stockQuantity;
    private int requiredQuantity;
}
