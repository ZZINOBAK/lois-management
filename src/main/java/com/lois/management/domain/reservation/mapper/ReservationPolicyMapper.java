package com.lois.management.domain.reservation.mapper;

import com.lois.management.domain.reservation.ReservationPolicy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReservationPolicyMapper {
    int insert(ReservationPolicy reservationPolicy);

    ReservationPolicy selectLatestPolicy();
    ReservationPolicy selectLatestPolicyWithLock();

    int update();

    int delete(Long id);


}
