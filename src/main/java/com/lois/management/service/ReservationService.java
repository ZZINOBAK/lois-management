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

    public List<Reservation> findAllOrderByPickUpTime() {
        return reservationMapper.findAllOrderByPickUpTime();
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
}
