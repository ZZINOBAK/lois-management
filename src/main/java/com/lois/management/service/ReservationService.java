package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.domain.dto.ReservationCreateReq;
import com.lois.management.domain.dto.ReservationRes;
import com.lois.management.domain.dto.ReservationSummaryRes;
import com.lois.management.domain.dto.ReservationUpdateReq;
import com.lois.management.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationMapper reservationMapper;

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

    public void update(Long id, ReservationUpdateReq req) {
        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new NoSuchElementException("Reservation not found with id: " + id);
        }
        if (req.resDate() != null) r.setResDate(LocalDate.parse(req.resDate()));
        if (req.resTime() != null) r.setResTime(LocalTime.parse(req.resTime()));
        if (req.contact() != null) r.setContact(req.contact());
        if (req.paid() != null) r.setPaid(req.paid());
        if (req.status() != null) r.setStatus(req.status());
        if (req.note() != null) r.setNote(req.note());
        reservationMapper.update(r);
    }

    public ReservationRes findOne(Long id) {
        Reservation r = reservationMapper.findById(id);
        if (r == null) {
            throw new NoSuchElementException("Reservation not found with id: " + id);
        }
        return new ReservationRes(r.getId(), r.getStatus());
    }

    public List<ReservationSummaryRes> findAllSummaries() {
        return reservationMapper.findAll().stream()
                .map(r -> new ReservationSummaryRes(
                        r.getId(),
                        r.getCakeId(),
                        r.getResDate() != null ? r.getResDate().toString() : null,
                        r.getResTime() != null ? r.getResTime().toString() : null,
                        r.getStatus()
                ))
                .toList();
    }


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

    public void update1(Long id) {
        reservationMapper.update1(id);
    }

    public void pickedUp(Long id) {
        reservationMapper.pickedUp(id);
    }

    public void delete(Long id) {
        reservationMapper.delete(id);
    }

    public void sortByPickUpTime() {
        reservationMapper.sortByPickUpTime();
    }

    public void deleteById(Long id) {
    }

    public List<Reservation> findTodayOrderByPickUpTime() {
        return reservationMapper.findTodayOrderByPickUpTime();
    }

    public List<Reservation> findAllOrderByPickUpTime() {
        return reservationMapper.findAllOrderByPickUpTime();
    }
}
