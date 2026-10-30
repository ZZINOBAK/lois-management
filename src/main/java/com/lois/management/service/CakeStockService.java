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
            // 스톡 증가 중 문제 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }
        if (cakeMovementMapper.insertProductionMovement(cakeId, cakeSize, "PRODUCED", true, requestId) != 1) {
            // 케이크 제작 완료 이력 저장 중 문제 발생
            throw new IllegalStateException("Expected exactly one production movement");
        }
    }

    @Transactional
    public boolean cancelProduction(Long cakeId, Integer cakeSize) {
        String requestId = UUID.randomUUID().toString();

        // 스톡 수량 확인
        Integer quantity = cakeStockMapper.findQuantityForUpdate(cakeId, cakeSize);
        if (quantity == null || quantity == 0) {
            // 재고 없음: 스톡 감소 불가
            return false;
        }
        if (cakeStockMapper.decreaseStock(cakeId, cakeSize) != 1) {
            // 스톡 감소 중 문제 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }
        if (cakeMovementMapper.insertProductionMovement(cakeId, cakeSize, "UNDO_PRODUCED", true, requestId) != 1) {
            // 케이크 제작 취소 이력 저장 중 문제 발생
            throw new IllegalStateException("Expected exactly one production movement");
        }
        return true;
    }
}
