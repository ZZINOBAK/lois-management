package com.lois.management.domain.cake.mapper;

import com.lois.management.domain.cake.CakeMovement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CakeStockMapper {
    int increaseStock(@Param("cakeId") Long cakeId, @Param("cakeSize") Integer cakeSize);

    Integer findQuantityForUpdate(@Param("cakeId") Long cakeId, @Param("cakeSize") Integer cakeSize);

    int decreaseStock(@Param("cakeId") Long cakeId, @Param("cakeSize") Integer cakeSize);

    // 일일 초기화: 전체 재고 조회 with 행 락 (cakeId, cakeSize, stock=현재 수량)
    List<CakeMovement> findAllStocksForUpdate();

    // 일일 초기화: 전체 재고 0
    int resetAllStocks();
}
