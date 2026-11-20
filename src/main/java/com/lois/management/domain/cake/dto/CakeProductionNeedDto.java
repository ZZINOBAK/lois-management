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
    // 1호 제작 필요 수량 표시 대상 판단용, 2호는 항상 true
    private boolean todayReserved;
}
