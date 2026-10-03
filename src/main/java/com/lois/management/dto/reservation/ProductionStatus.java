package com.lois.management.dto.reservation;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class ProductionStatus {
    // 1 오늘 예약 수량
    private Map<Integer, Map<Long, Integer>> demand;

    // 2 오늘 제작 완료 수량(예약건 + 여분 + 등)
    private Map<Integer, Map<Long, Integer>> produced;

    // 3 제작 완료 + 아직 픽업되지 않은 예약
    private Map<Integer, Map<Long, Integer>> pickupReady;

    // 4 아직 만들어야 하는 예약
    private Map<Integer, Map<Long, Integer>> toMake;

    // 5 예약 없이 판매 가능한 여분
    private Map<Integer, Map<Long, Integer>> extraStock;
}
