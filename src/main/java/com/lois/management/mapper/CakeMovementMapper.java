package com.lois.management.mapper;

import com.lois.management.domain.CakeMovement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.security.core.parameters.P;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CakeMovementMapper {

    int insertMovement(CakeMovement m);

    // 케이크 픽업 취소 시 스톡 변경 여부, move_type 조회 with 행 락
    CakeMovement findLatestPickupMovementForUpdate(@Param("reservationId") Long reservationId);

    // 케이크 변동 이력 저장 with reservationId: 픽업 여부
    int insertPickupMovement(@Param("cakeId") Long cakeId,
                             @Param("cakeSize") Integer cakeSize,
                             @Param("moveType") String moveType,
                             @Param("stockChanged") boolean stockChanged,
                             @Param("reservationId") Long reservationId,
                             @Param("requestId") String requestId);

    // 케이크 변동 이력 저장 without reservationId: 제작 여부, 현장 판매
    int insertStockMovement(@Param("cakeId") Long cakeId,
                                 @Param("cakeSize") Integer cakeSize,
                                 @Param("moveType") String moveType,
                                 @Param("stockChanged") boolean stockChanged,
                                 @Param("requestId") String requestId);

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

    List<Long> getReservationIdsByProduced(@Param("bizDate") LocalDate bizDate,
                                      @Param("cakeId") Long cakeId,
                                      @Param("cakeSize") Integer cakeSize);

    int getExtraStockByKey(@Param("bizDate") LocalDate bizDate,
                           @Param("cakeId") Long cakeId,
                           @Param("cakeSize") Integer cakeSize);

    List<CakeMovement> sumProducedByDate(@Param("bizDate") LocalDate bizDate);

    List<CakeMovement> sumPickupReadyByDate(@Param("bizDate") LocalDate bizDate);

    List<CakeMovement> sumExtraStockByDate(@Param("bizDate") LocalDate bizDate);
}
