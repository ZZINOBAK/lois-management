package com.lois.management.service;

import com.lois.management.mapper.CakeMovementMapper;
import com.lois.management.mapper.CakeStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CakeStockService {
    private final CakeStockMapper cakeStockMapper;
    private final CakeMovementMapper cakeMovementMapper;

    @Transactional
    public void produce(Long cakeId, Integer cakeSize) {
        String requestId = UUID.randomUUID().toString();
        if (cakeStockMapper.increaseStock(cakeId, cakeSize) != 1) {
            //스톡 증가 중 문제 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }
        if (cakeMovementMapper.insertProductionMovement(cakeId, cakeSize, "PRODUCED", true, requestId) != 1) {
            //케이크 제작 완료 이력 저장 중 문제 발생
            throw new IllegalStateException("Expected exactly one production movement");
        }
    }
}
