package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.domain.dto.ReservationCreateReq;
import com.lois.management.dto.reservation.ReservationRes;
import com.lois.management.dto.reservation.ReservationSummaryRes;
import com.lois.management.dto.reservation.ReservationUpdateReq;
import com.lois.management.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationMapper reservationMapper;


    public void create(Reservation reservation) {
        reservationMapper.insert(reservation);
    }

    public void createOnSite(Reservation reservation) {
        // 1️⃣ 날짜 / 시간 (현장판매 → 서버 기준)
        if (reservation.getResDate() == null) {
            reservation.setResDate(LocalDate.now());
        }

        if (reservation.getResTime() == null) {
            reservation.setResTime(LocalTime.now());
        }

        // 2️⃣ 연락처 (현장판매 기본값)
        if (reservation.getContact() == null || reservation.getContact().isBlank()) {
            reservation.setContact("010-0000-0000");
        }

        // 3️⃣ 결제 여부 (현장판매는 즉시 결제)
        if (reservation.getPaid() == null) {
            reservation.setPaid(true); // 또는 true (컬럼 타입에 맞게)
        }

        reservationMapper.insertOnSite(reservation);
    }

    public List<Reservation> findAll() {
        return reservationMapper.findAll();
    }

    public Reservation findById(Long id) {
        return reservationMapper.findById(id);
    }

    public List<Cake> findAllCakeFlavor() {
        return reservationMapper.findAllCakeFlavor();
    }

    public void update(Long id, Reservation reservation) {
        reservationMapper.update(id, reservation);
    }

    @Transactional
    public void togglePickupStatus(Long id) {
        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new NoSuchElementException("Reservation not found");
        }

        if ("PICKED".equals(r.getPickupStatus())) {
            // 픽업완료 → 픽업예정
            reservationMapper.updatePickupStatus(id, "WAITING", null);
        } else {
            // 픽업예정 → 픽업완료
            reservationMapper.updatePickupStatus(id, "PICKED", LocalDateTime.now());
        }
    }

    @Transactional
    public void toggleMakeStatus(Long id) {
        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new NoSuchElementException("Reservation not found");
        }

        if ("READY".equals(r.getMakeStatus())) {
            reservationMapper.updateMakeStatus(id, "RESERVED");
        } else {
            reservationMapper.updateMakeStatus(id, "READY");
        }
    }

    public void delete(Long id) {
        reservationMapper.delete(id);
    }

    public void sortByPickUpTime() {
        reservationMapper.sortByPickUpTime();
    }

    public List<Reservation> findTodayOrderByPickUpTime() {
        return reservationMapper.findTodayOrderByPickUpTime();
    }

    public List<Reservation> findFromTodayOrderByPickUpTime() {
        return reservationMapper.findFromTodayOrderByPickUpTime(LocalDate.now());

    }


    public void deleteById(Long id) {
    }





//API GPT 코드
    public Long create(ReservationCreateReq req) {
        Reservation r = new Reservation();
        r.setCakeId(req.cakeId());
        r.setResDate(LocalDate.parse(req.resDate()));
        r.setResTime(LocalTime.parse(req.resTime()));
        r.setContact(req.contact());
        r.setPaid(Boolean.TRUE.equals(req.paid()));

        // DB 저장 (기존 void 메서드 그대로 재사용)
        create(r);

        // ✅ 여기서 r.getId()를 직접 반환
        // (MyBatis의 <selectKey>로 id가 자동 채워진 경우)
        return r.getId();
    }

    public void updateApi(Long id, ReservationUpdateReq req) {
        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new NoSuchElementException("Reservation not found with id: " + id);
        }
        if (req.resDate() != null) r.setResDate(LocalDate.parse(req.resDate()));
        if (req.resTime() != null) r.setResTime(LocalTime.parse(req.resTime()));
        if (req.contact() != null) r.setContact(req.contact());
        if (req.paid() != null) r.setPaid(req.paid());
        if (req.status() != null) r.setMakeStatus(req.status());
        if (req.note() != null) r.setNote(req.note());
        reservationMapper.updateApi(r);
    }

    public ReservationRes findOne(Long id) {
        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new NoSuchElementException("Reservation not found with id: " + id);
        }
        return new ReservationRes(r.getId(), r.getMakeStatus());
    }

    public List<ReservationSummaryRes> findAllSummaries() {
        return reservationMapper.findAll().stream()
                .map(r -> new ReservationSummaryRes(
                        r.getId(),
                        r.getCakeId(),
                        r.getResDate() != null ? r.getResDate().toString() : null,
                        r.getResTime() != null ? r.getResTime().toString() : null,
                        r.getMakeStatus()
                ))
                .toList();
    }

    public List<Reservation> findByContactSuffix(String contactSuffix) {
        return reservationMapper.findByContactSuffix(contactSuffix);

    }

    public List<Reservation> findByPickupStatus(String pickupStatus) {
        return reservationMapper.findByPickupStatus(pickupStatus);
    }

    public boolean existsExactSameReservation(Reservation reserve) {
        return reservationMapper.existsExactSameReservation(reserve);

    }

    public boolean existsByContact(String contact) {
        return reservationMapper.existsByContact(contact);
    }

    public List<Reservation> findByDateOrderByPickUpTime(LocalDate date) {
        return reservationMapper.findByDateOrderByPickUpTime(date);
    }

    public List<Reservation> findTodayOrderByCreatedAtDesc() {
        return reservationMapper.findTodayOrderByCreatedAtDesc();
    }

    public List<Reservation> findFromTodayOrderByCreatedAtDesc() {
        return reservationMapper.findFromTodayOrderByCreatedAtDesc();

    }

    public List<Reservation> findByDateOrderByCreatedAtDesc(LocalDate date) {
        return reservationMapper.findByDateOrderByCreatedAtDesc(date);

    }

    public List<Reservation> findTodayByPickupStatusWaiting() {
        return reservationMapper.findTodayByPickupStatusWaiting();

    }

    public List<Reservation> findFromTodayByPickupStatusWaiting() {
        return reservationMapper.findFromTodayByPickupStatusWaiting();

    }

    public List<Reservation> findByDateAndPickupStatusWaiting(LocalDate date) {
        return reservationMapper.findByDateAndPickupStatusWaiting(date);

    }
}
