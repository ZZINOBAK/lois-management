package com.lois.management.domain.cake.dto;

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
