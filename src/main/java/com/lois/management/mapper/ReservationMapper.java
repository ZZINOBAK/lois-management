package com.lois.management.mapper;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReservationMapper {

    void insert(Reservation reservation);

    List<Reservation> findAll();

    Reservation findById(Long id);


    List<Cake> findAllCakeFlavor();


//    ScopedValue<Object> findById(Long id);

    void update(Reservation r);

    void update1(Long id);


    void pickedUp(Long id);

    void delete(Long id);

    void sortByPickUpTime();

    List<Reservation> findTodayOrderByPickUpTime();

    List<Reservation> findAllOrderByPickUpTime();
}
