package com.lois.management.mapper;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReservationMapper {

    void insert(Reservation reservation);

    List<Reservation> findAll();

    List<Cake> findAllCakeFlavor();
}
