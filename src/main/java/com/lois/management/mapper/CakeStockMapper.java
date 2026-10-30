package com.lois.management.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CakeStockMapper {
    int increaseStock(@Param("cakeId") Long cakeId, @Param("cakeSize") Integer cakeSize);

    Integer findQuantityForUpdate(@Param("cakeId") Long cakeId, @Param("cakeSize") Integer cakeSize);

    int decreaseStock(@Param("cakeId") Long cakeId, @Param("cakeSize") Integer cakeSize);
}
