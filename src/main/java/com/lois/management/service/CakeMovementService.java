package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.domain.CakeMovement;
import com.lois.management.domain.Reservation;
import com.lois.management.mapper.CakeMovementMapper;
import com.lois.management.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CakeMovementService {

    private final CakeMovementMapper cakeMovementMapper;
    private final ReservationMapper reservationMapper;

    private final ReservationService reservationService;
    private final CakeService cakeService; // cakeId->flavor 매핑용

    /**
     * 1) 생산 +N
     */
    @Transactional
    public void produce(String requestId, Long cakeId, Integer cakeSize, String note, Long reservationId) {
        Long reservtionsId = reservationId;
        LocalDate bizDate = LocalDate.now(ZoneId.of("Asia/Seoul"));

        if (requestId != null && requestId.startsWith("MANU-")) {
            //오늘 예약 건 중 make_status가 RESERVED인 예약 중 가장 예약 시간이 빠른 건 한개의 make_status를 READY로 변경
            reservtionsId = reservationMapper.findReservationIdForProduce(bizDate, cakeId, cakeSize);
            reservationMapper.updateWithProduce(reservtionsId);
        }

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setMemo(note);
        m.setDelta(1);
        m.setMoveType("PRODUCED");
        m.setRequestId(requestId);
        m.setReservationId(reservtionsId);

        cakeMovementMapper.insertMovement(m);


        log.info("제작완료 : {} ", cakeMovementMapper.findById(m.getId()));
    }

    @Transactional
    public void adjust(String requestId, Long cakeId, Integer cakeSize, String memo, Long reservationId) {

        LocalDate bizDate = LocalDate.now(ZoneId.of("Asia/Seoul"));

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setRequestId(requestId);
        m.setMemo(memo);

        m.setDelta(-1);
        m.setMoveType("UNDO_PRODUCED");
        m.setReservationId(reservationId);
        cakeMovementMapper.insertMovement(m);
    }

    public Long findLastCoveredReservationId(
            List<Reservation> reservationsSorted,
            Map<Integer, Map<Long, Integer>> stockMap,
            Long targetCakeId,
            Integer targetSize
    ) {
        // 남은 재고
        int left = stockMap.getOrDefault(targetSize, Map.of())
                .getOrDefault(targetCakeId, 0);

        Long lastCoveredId = null;

        for (Reservation r : reservationsSorted) {
            if (!r.getCakeId().equals(targetCakeId)) continue;
            if (r.getCakeSize() != targetSize) continue;

            if (left > 0) {
                lastCoveredId = r.getId();
                left--;
            } else {
                break;
            }
        }
        return lastCoveredId;
    }


    /**
     * 2) 현장판매 -N
     * movements-only라서 "현재 재고"를 SUM으로 확인하고 부족하면 막는다.
     * (동시성 100% 방어는 cache 버전에서 더 완벽해짐)
     */
    @Transactional
    public void sellOnSite(LocalDate bizDate, Long cakeId, Integer cakeSize, String requestId, String note) {

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(-1);
        m.setMoveType("PICKED");
        m.setRequestId(requestId);

        int stock = cakeMovementMapper.getStockByKey(bizDate, cakeId, cakeSize);
        if (stock < 1) {
            produce(requestId + "-1", cakeId, cakeSize, "AUTO_PRODUCE_ON_SITE", 0L);
            m.setMemo(note);
        } else {
            Long reservationId = reservationService.readyToReserved(cakeId, cakeSize, bizDate);
            m.setMemo(note + "예약 번호" + reservationId + " 제작상태 READY -> RESERVED");
        }
        cakeMovementMapper.insertMovement(m);
    }

    @Transactional
    public void sellOnSiteWithReservationAdjust(Long cakeId, Integer cakeSize, String note) {
        LocalDate today = LocalDate.now();
        String requestId = "ONSITE-" + System.currentTimeMillis();
        sellOnSite(today, cakeId, cakeSize, requestId, note);
    }

    /**
     * 3) 픽업 처리(예약 1건)
     * - reservations.pickup_status='PICKED'
     * - 재고 -1 (PICKUP)
     */
    @Transactional
    public void pickupReservation(Long reservationId, String requestId) {

        Reservation r = reservationService.findById(reservationId);
        if (r == null) throw new IllegalArgumentException("예약 없음 id=" + reservationId);

        // 이미 픽업이면 중복 방지
        if ("PICKED".equals(r.getPickupStatus())) {
            return;
        }

        // 재고 체크: (cakeId, cakeSize)
        Long cakeId = r.getCakeId();
        Integer cakeSize = r.getCakeSize();
        LocalDate bizDate = r.getResDate();

        int stock = cakeMovementMapper.getStockByKey(bizDate, cakeId, cakeSize);
        if (stock < 1) {
            reservationService.toggleMakeStatus(reservationId);
            log.info("픽업으로 인한 makeStatus 상태 변경 : RESERVED -> READY");
        }

        // 1) 예약 상태 변경
        reservationService.updatePickupStatus(reservationId, "PICKED", LocalDateTime.now());

        // 2) movement 기록
        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(-1);
        m.setMoveType("PICKUP");
        m.setReservationId(reservationId);
        m.setRequestId(requestId);

        cakeMovementMapper.insertMovement(m);
    }

    @Transactional
    public void togglePickupReservation(Long reservationId, String requestId) {

        Reservation r = reservationService.findById(reservationId);
        if (r == null) throw new IllegalArgumentException("예약 없음 id=" + reservationId);

        Long cakeId = r.getCakeId();
        Integer cakeSize = r.getCakeSize();
        LocalDate bizDate = r.getResDate();

        CakeMovement m = new CakeMovement();
        boolean isPicked = "PICKED".equals(r.getPickupStatus());

        if (!isPicked) {
            // WAITING -> PICKED : 재고 -1
            int stock = cakeMovementMapper.getStockByKey(bizDate, cakeId, cakeSize);
            List<Long> rIdsProduced = cakeMovementMapper.getReservationIdsByProduced(bizDate, cakeId, cakeSize);

            if (stock < 1 || !rIdsProduced.contains(reservationId)) {
                reservationService.toggleMakeStatus(reservationId);
                log.info("픽업으로 인한 makeStatus 상태 변경 : RESERVED -> READY");

                produce(requestId + "-1", cakeId, cakeSize,  "AUTO_PRODUCE_ON_PICKUP", reservationId);
            }
            reservationService.updatePickupStatus(reservationId, "PICKED", LocalDateTime.now());

            m.setBizDate(bizDate);
            m.setCakeId(cakeId);
            m.setCakeSize(cakeSize);
            m.setDelta(-1);
            m.setMoveType("PICKED");
            m.setReservationId(reservationId);
            m.setRequestId(requestId);
            cakeMovementMapper.insertMovement(m);

            return;
        }

        // PICKED -> WAITING : 취소(원복) => 재고 +1
        reservationService.updatePickupStatus(reservationId, "WAITING", LocalDateTime.now());

        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(1);
        m.setMoveType("UNDO_PICKED");
        m.setReservationId(reservationId);
        m.setRequestId(requestId);
        cakeMovementMapper.insertMovement(m);
    }


    /**
     * 4) toMakeMap 계산: demand(WAITING 예약 수요) - stock(현재 재고)
     * 결과: size -> flavor -> qty
     */
    @Transactional(readOnly = true)
    public Map<Integer, Map<Long, Integer>> calcToMakeMap(LocalDate today) {
        // 오늘 총 예약건 조회(pickUpStatus==WAITING)
        List<Reservation> demandRows = reservationService.countDemandByDate(today);
        // 오늘 만들어져 있는 케이크 조회 : sumStockByDate(SUM(delta))
        List<CakeMovement> stockRows = cakeMovementMapper.sumStockByDate(today);
        // 케이크 테이블에서 케이크 id, 맛 조회
        List<Cake> cakeIdToFlavor = cakeService.findAllIdFlavor();

        // 오늘 총 예약 건 : <size,<cakeId,demand>>
        Map<Integer, Map<Long, Integer>> demandMap = new HashMap<>();
        for (Reservation row : demandRows) {
            demandMap.computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getCnt());
        }
        // 오늘 만들어져 있는 케이크 : <size,<cakeId,stock>> {사이즈 = {케이크아이디 = 재고, ...}}
        Map<Integer, Map<Long, Integer>> stockMap = new HashMap<>();
        for (CakeMovement row : stockRows) {
            stockMap.computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getStock());
        }

        // 몇개 더 만들어야 하는가 계산 : <size,<cakeId,toMake>> -> toMake = demand - stock
        Map<Integer, Map<Long, Integer>> toMakeResult = new HashMap<>();
        // demandMap : <size,<cakeId,demand>>
        for (Map.Entry<Integer, Map<Long, Integer>> reservations : demandMap.entrySet()) {
            int size = reservations.getKey();
            Map<Long, Integer> reservedCakeIdNAmount = reservations.getValue();
            // stockMap : <size,<cakeId,stock>>
            Map<Long, Integer> beMadeCakeIdNAmount = stockMap.getOrDefault(size, Map.of());
            Map<Long, Integer> toMakeCakeIdNAmount = new HashMap<>();
            for (Map.Entry<Long, Integer> e : reservedCakeIdNAmount.entrySet()) {
                Long reservedCakeId = e.getKey();
                int reservedAmount = e.getValue();
                int beMadeAmount = beMadeCakeIdNAmount.getOrDefault(reservedCakeId, 0);
                int toMake = Math.max(reservedAmount - beMadeAmount, 0);
                toMakeCakeIdNAmount.put(reservedCakeId, toMake);
            }
            toMakeResult.put(size, toMakeCakeIdNAmount);
        }
        log.info("[toMake] result={}", toMakeResult);
        return toMakeResult;
    }

    public Map<Integer, Map<Long, Integer>> calcStockMap(LocalDate bizDate) {
        // SUM(delta) : sumStockByDate
        List<CakeMovement> stockRows = cakeMovementMapper.sumStockByDate(bizDate);

        // cakeId, flavor 조회
        List<Cake> flavorByCakeId = cakeService.findAllIdFlavor();

        // 재고 계산 : <size,<cakeId,stock(SUM(delta)>>
        Map<Integer, Map<Long, Integer>> result = new HashMap<>();

        for (CakeMovement r : stockRows) {
            Integer size = r.getCakeSize();
            Long cakeId = r.getCakeId();
            int stock = r.getStock();


            result.computeIfAbsent(size, k -> new HashMap<>())
                    .put(cakeId, stock);
        }

        return result;
    }


}
