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

    @Transactional // 제작 완료
    public void produce(Long cakeId, Integer cakeSize) {
        // 스톡 증가
        if (cakeStockMapper.increaseStock(cakeId, cakeSize) != 1) {
            // 스톡 증가 중 예외 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }

        String requestId = UUID.randomUUID().toString();
        // 제작 완료 이력 저장
        if (cakeMovementMapper.insertStockMovement(cakeId, cakeSize, "PRODUCED", true, requestId) != 1) {
            // 제작 완료 이력 저장 중 예외 발생
            throw new IllegalStateException("Expected exactly one production movement");
        }
    }

    @Transactional // 제작 취소
    public boolean cancelProduction(Long cakeId, Integer cakeSize) {
        // 스톡 수량 확인
        Integer quantity = cakeStockMapper.findQuantityForUpdate(cakeId, cakeSize);
        if (quantity == null || quantity == 0) {
            // 재고 없음: 스톡 감소 불가
            return false;
        }

        // 스톡 감소
        if (cakeStockMapper.decreaseStock(cakeId, cakeSize) != 1) {
            // 스톡 감소 중 예외 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }

        String requestId = UUID.randomUUID().toString();
        // 제작 취소 이력 저장
        if (cakeMovementMapper.insertStockMovement(cakeId, cakeSize, "UNDO_PRODUCED", true, requestId) != 1) {
            // 제작 취소 이력 저장 중 예외 발생
            throw new IllegalStateException("Expected exactly one production movement");
        }
        return true;
    }

    @Transactional // 현장 판매
    public void sellOnSite(Long cakeId, Integer cakeSize) {
        // 스톡 수량 확인
        Integer quantity = cakeStockMapper.findQuantityForUpdate(cakeId, cakeSize);

        boolean stockChanged = quantity != null && quantity > 0;
        // 스톡 재고 있으면 스톡 감소
        if (stockChanged && cakeStockMapper.decreaseStock(cakeId, cakeSize) != 1) {
            // 스톡 감소 중 예외 발생
            throw new IllegalStateException("Expected exactly one stock row");
        }

        String requestId = UUID.randomUUID().toString();
        // 현장 판매 이력 저장
        if (cakeMovementMapper.insertStockMovement(cakeId, cakeSize, "ON_SITE_SOLD", stockChanged, requestId) != 1) {
            // 현장 판매 이력 저장 중 예외 발생
            throw new IllegalStateException("Expected exactly one on-site sale movement");
        }
    }
}
