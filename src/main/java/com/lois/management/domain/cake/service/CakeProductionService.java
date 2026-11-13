package com.lois.management.domain.cake.service;

import com.lois.management.domain.cake.dto.CakeProductionNeedDto;
import com.lois.management.domain.cake.mapper.CakeProductionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CakeProductionService {
    private final CakeProductionMapper cakeProductionMapper;

    @Transactional(readOnly = true)
    public List<CakeProductionNeedDto> findTodayProductionNeeds() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<CakeProductionNeedDto> needs = new ArrayList<>(cakeProductionMapper.findProductionNeeds(today, 2));
        needs.addAll(cakeProductionMapper.findProductionNeeds(today, 1));
        for (CakeProductionNeedDto need : needs) {
            need.setRequiredQuantity(Math.max(need.getReservationQuantity() - need.getStockQuantity(), 0));
        }
        return needs;
    }
}
