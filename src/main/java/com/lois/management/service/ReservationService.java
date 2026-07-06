package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.*;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.ReservationLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Slf4j
//@RequiredArgsConstructor
public class ReservationService {
    private final ReservationMapper reservationMapper;
    private final ReservationPolicyMapper reservationPolicyMapper;
//    @Qualifier("localLockConcurrencyGuard")
    private final ReservationLimiter reservationConcurrencyGuard;

    public ReservationService(ReservationMapper reservationMapper, ReservationPolicyMapper reservationPolicyMapper,
                             ReservationLimiter reservationConcurrencyGuard) {
        this.reservationMapper = reservationMapper;
        this.reservationPolicyMapper = reservationPolicyMapper;
        this.reservationConcurrencyGuard = reservationConcurrencyGuard;
    }

    @Transactional
    public void create(Reservation reservation) {
        System.out.println(
                "현재 Limiter = "
                        + reservationConcurrencyGuard.getClass().getSimpleName()
        );

        // 1. 주입된 매니저가 DB 방식이든 캐시 방식이든 알아서 검증합니다.
        if (!reservationConcurrencyGuard.tryAcquireSlot(reservation.getResDate(), reservation.getResTime())) {
            throw new IllegalArgumentException("예약 가능 수량을 초과했습니다.");
        }

        // 2. 검증 통과 시 최종 인서트
        reservationMapper.insert(reservation);
    }

//    @Transactional
//    public void create(Reservation reservation) {
//        // [STEP 1] DB에서 실시간 제한 정책 기준값 조회 (예: 시간당 10개, 당일 무제한)
////        ReservationPolicy policy = reservationPolicyMapper.selectLatestPolicy();
//        ReservationPolicy policy = reservationPolicyMapper.selectLatestPolicyWithLock();
//
//        if (policy != null) {
//            // 날짜와 시간대 파라미터 추출
//            LocalDate resDate = reservation.getResDate();
//            // 시간대 비교를 위해 LocalTime에서 시(Hour) 정보나 전체 시간 포맷을 활용합니다.
//            LocalTime resTime = reservation.getResTime();
//
//            // [STEP 2] DB를 조회하여 "당일" 현재 예약 완료된 총 건수 계산
//            if (policy.getDailyMaxLimit() != -1) { // -1이 아닐 때만 제한 체크
//                int currentDailyCount = reservationMapper.countByDate(resDate);
//                if (currentDailyCount >= policy.getDailyMaxLimit()) {
//                    throw new IllegalArgumentException("당일 예약 가능 수량을 초과했습니다. (최대: " + policy.getDailyMaxLimit() + "개)");
//                }
//            }
//
//            // [STEP 3] DB를 조회하여 "해당 시간대" 현재 예약 완료된 총 건수 계산
//            // (참고: reservationMapper에 특정 날짜/시간대 카운트 쿼리가 있다고 가정)
//            int currentHourlyCount = reservationMapper.countByDateAndTime(resDate, resTime);
//
////            try {
////                Thread.sleep(100);
////            } catch (InterruptedException e) {
////                Thread.currentThread().interrupt();
////            }
//
//            if (currentHourlyCount >= policy.getHourlyMaxLimit()) {
//                throw new IllegalArgumentException("해당 시간대 예약 가능 수량을 초과했습니다. (최대: " + policy.getHourlyMaxLimit() + "개)");
//            }
//        }
//
//        reservationMapper.insert(reservation);
//    }

    public void createOnSite(Reservation reservation) {

        // 날짜/시간은 서버에서 강제
        reservation.setResDate(LocalDate.now());
        reservation.setResTime(LocalTime.now().withSecond(0).withNano(0));

        // ✅ 현장판매는 이미 나간 케이크
        reservation.setPickupStatus("PICKED");   // 더 이상 WAITING 아님
        reservation.setMakeStatus("READY");      // 이미 만들어진 케이크

        // ✅ 현장판매 식별자
        reservation.setContact("ON_SITE");

        // 결제 완료
        reservation.setPaid(true);

        log.info("reservation = {}", reservation);

        reservationMapper.insertOnSite(reservation);
    }

    public List<Reservation> findAll(ReservationPageReq pageRequest) {
        pageRequest.setTotalCount(reservationMapper.countAll());
        pageRequest.calculate();
        return reservationMapper.findAll(pageRequest);
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

        log.info("예약 제작 상태 변경 -> 예약 아이디= {}", r.getId());
    }

    public void delete(Long id) {
        reservationMapper.delete(id);
    }

    public void sortByPickUpTime() {
        reservationMapper.sortByPickUpTime();
    }

    public List<Reservation> findTodayOrderByPickUpTime(LocalDate today) {
        return reservationMapper.findTodayOrderByPickUpTime(today);
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

    public List<ReservationSummaryRes> findAllSummaries(ReservationPageReq pageRequest) {
        return reservationMapper.findAll(pageRequest).stream()
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

    public List<Reservation> findTodayOrderByCreatedAtDesc(LocalDate today) {
        return reservationMapper.findTodayOrderByCreatedAtDesc(today);
    }

    public List<Reservation> findFromTodayOrderByCreatedAtDesc() {
        return reservationMapper.findFromTodayOrderByCreatedAtDesc();

    }

    public List<Reservation> findByDateOrderByCreatedAtDesc(LocalDate date) {
        return reservationMapper.findByDateOrderByCreatedAtDesc(date);

    }

    public List<Reservation> findTodayByPickupStatusWaiting(LocalDate date) {
        return reservationMapper.findTodayByPickupStatusWaiting(date);

    }

    public List<Reservation> findFromTodayByPickupStatusWaiting() {
        return reservationMapper.findFromTodayByPickupStatusWaiting();

    }

    public List<Reservation> findByDateAndPickupStatusWaiting(LocalDate date) {
        return reservationMapper.findByDateAndPickupStatusWaiting(date);

    }

    public Map<Integer, Map<String, Integer>> calcToMakeBySizeAndFlavor(List<Reservation> reservations, LocalDate date) {

        Map<Integer, Map<String, Integer>> result = new HashMap<>();

        for (Reservation r : reservations) {
            if (r.getCakeFlavor() == null) continue;

            // WAITING만 집계 대상으로
            if (r.getPickupStatus() == null || !"WAITING".equals(r.getPickupStatus())) {
                continue;
            }
            if ("ON_SITE".equals(r.getContact())) continue;   // ★ 추가

            int size = r.getCakeSize();
            String flavor = r.getCakeFlavor();

            result.computeIfAbsent(size, k -> new HashMap<>());
            Map<String, Integer> flavorMap = result.get(size);

            int v = flavorMap.getOrDefault(flavor, 0);
            v += 1;
            if ("READY".equals(r.getMakeStatus())) v -= 1;
            if (v < 0) v = 0;

            flavorMap.put(flavor, v);
        }

        // 2) ON_SITE 수량은 +로 더한다 (핵심)
        List<Map<String, Object>> rows = reservationMapper.countTodayOnSiteBySizeAndFlavor(date);
        for (Map<String, Object> row : rows) {
            Integer size = toInt(row.get("cakeSize"));
            String flavor = (String) row.get("cakeFlavor");
            Integer cnt = toInt(row.get("cnt"));

            if (size == null || flavor == null || cnt == null) continue;

            result.computeIfAbsent(size, k -> new HashMap<>());
            Map<String, Integer> flavorMap = result.get(size);
            flavorMap.put(flavor, flavorMap.getOrDefault(flavor, 0) + cnt);
        }

        log.info("계산한값: {}", result);
        return result;
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return l.intValue();
        if (o instanceof java.math.BigDecimal bd) return bd.intValue();
        if (o instanceof String s) return Integer.parseInt(s);
        return null;
    }

    public List<Reservation> findTodayForToMakeCalc(LocalDate today) {
        return reservationMapper.findTodayForToMakeCalc(today);
    }

    public List<Reservation> countDemandByDate(LocalDate today) {
        return reservationMapper.countDemandByDate(today);
    }

    public void updatePickupStatus(Long reservationId, String picked, LocalDateTime now) {
        reservationMapper.updatePickupStatus(reservationId, picked, now);
    }

    public Map<Long, Boolean> markProducedUi(List<Reservation> reservations, Map<Integer, Map<Long, Integer>> stockMap) {
        Map<Integer, Map<Long, Integer>> remain = new HashMap<>();
        stockMap.forEach((size, m) -> remain.put(size, new HashMap<>(m)));

        Map<Long, Boolean> producedByReservationId = new HashMap<>();

        for (Reservation r : reservations) {
            int size = r.getCakeSize();
            long cakeId = r.getCakeId();

            int left = remain
                    .getOrDefault(size, Map.of())
                    .getOrDefault(cakeId, 0);

            boolean produced = left > 0;

            if (produced) {
                remain.get(size).put(cakeId, left - 1);
            }

            producedByReservationId.put(r.getId(), produced);
        }
        return producedByReservationId;
    }


    public Long readyToReserved(Long cakeId, Integer cakeSize, LocalDate today) {
        Reservation reservation = reservationMapper.findByCakeIdNCakeSizeToday(cakeId, cakeSize, today);
        if (reservation == null) {
            return 0L;
        }
        toggleMakeStatus(reservation.getId());
        return reservation.getId();
    }
}
