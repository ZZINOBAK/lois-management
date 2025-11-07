package com.lois.management.mapper;

import com.lois.management.domain.Reservation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReservationMapper {

    void insert(Reservation reservation);

}
