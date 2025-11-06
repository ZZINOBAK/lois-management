package com.lois.management.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.ui.Model;

@Mapper
public interface ReservationMapper {

    void insert(Model model);

}
