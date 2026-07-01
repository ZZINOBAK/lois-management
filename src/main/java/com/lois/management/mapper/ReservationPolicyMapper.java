package com.lois.management.mapper;

import com.lois.management.domain.Reservation;
import com.lois.management.domain.ReservationPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReservationPolicyMapper {
    int insert(ReservationPolicy reservationPolicy);

    ReservationPolicy selectLatestPolicy();
    ReservationPolicy selectLatestPolicyWithLock();

    int update();

    int delete(Long id);


}
