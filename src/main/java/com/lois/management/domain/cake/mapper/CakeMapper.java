package com.lois.management.domain.cake.mapper;

import com.lois.management.domain.cake.Cake;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CakeMapper {

    List<String> findAllFlavors();

    // ✅ 추가: id, flavor만 가볍게 조회
    List<Cake> findAllIdFlavor();

    List<Cake> findFlavorsForDashboard();
}
