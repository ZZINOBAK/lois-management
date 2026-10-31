package com.lois.management.service;

import com.lois.management.dto.production.CakeProductionNeedDto;
import com.lois.management.mapper.CakeProductionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CakeProductionService {
    private final CakeProductionMapper cakeProductionMapper;

    @Transactional(readOnly = true)
    public List<CakeProductionNeedDto> findTodayProductionNeeds() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<CakeProductionNeedDto> needs = cakeProductionMapper.findProductionNeeds(today, 2);
        for (CakeProductionNeedDto need : needs) {
            need.setRequiredQuantity(Math.max(need.getReservationQuantity() - need.getStockQuantity(), 0));
        }
        return needs;
    }
}
