package com.lois.management.mapper;

import com.lois.management.domain.CakeMovement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CakeMovementMapper {

    int insertMovement(CakeMovement m);

    /**
     * 오늘(또는 지정일) 재고 = SUM(delta)
     * (cake_size, cake_id) 단위로 집계
     */
    List<CakeMovement> sumStockByDate(@Param("today") LocalDate today);

    /**
     * 특정 케이크/사이즈 현재 재고 (재고 부족 체크용)
     */
    Integer getStockByKey(@Param("bizDate") LocalDate bizDate,
                          @Param("cakeId") Long cakeId,
                          @Param("cakeSize") Integer cakeSize);

    CakeMovement findById(Long id);

    void addReservationId(@Param("id") Long id, @Param("reservationId") Long reservationIdForManu);
}
