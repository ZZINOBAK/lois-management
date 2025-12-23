package com.lois.management.mapper;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    void insert(Reservation reservation);

    List<Reservation> findAll();

    Reservation findById(Long id);


    List<Cake> findAllCakeFlavor();


//    ScopedValue<Object> findById(Long id);

    void updateApi(Reservation r);

    void update(@Param("id") Long id, @Param("reservation") Reservation reservation);

    void updatePickupStatus(@Param("id") Long id, @Param("pickupStatus") String pickupStatus, @Param("pickedUpAt") Object o);

    void updateMakeStatus(@Param("id") Long id, @Param("makeStatus")String reserved);

    void delete(Long id);

    void sortByPickUpTime();

    List<Reservation> findTodayOrderByPickUpTime();

    List<Reservation> findAllOrderByPickUpTime();


    List<Reservation> findByContactSuffix(String contactSuffix);

    List<Reservation> findByPickupStatus(String pickupStatus);

    boolean existsExactSameReservation(Reservation reserve);

    boolean existsByContact(String contact);

    List<Reservation> findFromTodayOrderByPickUpTime(LocalDate now);

    List<Reservation> findByDateOrderByPickUpTime(LocalDate date);

    List<Reservation> findTodayOrderByCreatedAtDesc();

    List<Reservation> findFromTodayOrderByCreatedAtDesc();

    List<Reservation> findByDateOrderByCreatedAtDesc(LocalDate date);

    List<Reservation> findTodayByPickupStatusWaiting();

    List<Reservation> findFromTodayByPickupStatusWaiting();

    List<Reservation> findByDateAndPickupStatusWaiting(LocalDate date);

    void insertOnSite(Reservation reservation);

    List<Reservation> findTodayForToMakeCalc();
    List<Reservation> findByDateForToMakeCalc(LocalDate date);
}
