package com.lois.management.domain.cake.mapper;

import com.lois.management.domain.cake.dto.CakeProductionNeedDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CakeProductionMapper {
    List<CakeProductionNeedDto> findProductionNeeds(@Param("today") LocalDate today,
                                                   @Param("cakeSize") Integer cakeSize);
}
