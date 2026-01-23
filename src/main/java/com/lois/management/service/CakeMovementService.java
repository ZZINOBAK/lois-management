package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.domain.CakeMovement;
import com.lois.management.domain.Reservation;
import com.lois.management.mapper.CakeMovementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CakeMovementService {

    private final CakeMovementMapper cakeMovementMapper;

    private final ReservationService reservationService;
    private final CakeService cakeService; // cakeId->flavor 매핑용

    /**
     * 1) 생산 +N
     */
    @Transactional
    public void produce(LocalDate bizDate, Long cakeId, Integer cakeSize, int amount, String requestId, String memo) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(amount);
        m.setMoveType("PRODUCED");
        m.setRequestId(requestId);
        m.setMemo(memo);

        cakeMovementMapper.insertMovement(m);
        log.info("제작완료 : {} ", cakeMovementMapper.findById(m.getId()));
    }

    @Transactional
    public void adjust(
            LocalDate bizDate,
            Long cakeId,
            Integer cakeSize,
            int delta,
            String requestId,
            String memo
    ) {
        if (delta >= 0) {
            throw new IllegalArgumentException("adjust는 음수 delta만 허용됩니다.");
        }

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(delta); // -1
        m.setMoveType("UNDO_PRODUCED"); // 또는 ADJUST
        m.setRequestId(requestId);
        m.setMemo(memo);

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
    public void sellOnSite(LocalDate bizDate, Long cakeId, Integer cakeSize, int amount, String requestId, String memo) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");

        int stock = cakeMovementMapper.getStockByKey(bizDate, cakeId, cakeSize);
        if (stock < amount) {

            produce(bizDate, cakeId, cakeSize, 1, requestId + "onSite", "from reservation list");
        }

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(-amount);
        m.setMoveType("ON_SITE");
        m.setRequestId(requestId);
        m.setMemo(memo);

        cakeMovementMapper.insertMovement(m);
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

        boolean isPicked = "PICKED".equals(r.getPickupStatus());

        if (!isPicked) {
            // WAITING -> PICKED : 재고 -1
            int stock = cakeMovementMapper.getStockByKey(bizDate, cakeId, cakeSize);
            if (stock < 1) {
                reservationService.toggleMakeStatus(reservationId);
                log.info("픽업으로 인한 makeStatus 상태 변경 : RESERVED -> READY");

                produce(bizDate, cakeId, cakeSize, 1, requestId + "onTime", "from reservation list");

            }

            reservationService.updatePickupStatus(reservationId, "PICKED", LocalDateTime.now());

            CakeMovement m = new CakeMovement();
            m.setBizDate(bizDate);
            m.setCakeId(cakeId);
            m.setCakeSize(cakeSize);
            m.setDelta(-1);
            m.setMoveType("PICKUP");
            m.setReservationId(reservationId);
            m.setRequestId(requestId);
            cakeMovementMapper.insertMovement(m);

            return;
        }

        // PICKED -> WAITING : 취소(원복) => 재고 +1
        reservationService.updatePickupStatus(reservationId, "WAITING", null);

//        reservationService.toggleMakeStatus(reservationId);
//        log.info("언픽업으로 인한 makeStatus 상태 변경 : READY -> RESERVED");
//        adjust(bizDate, cakeId, cakeSize, -1,  requestId + "onTime", "undo from reservation list");

        CakeMovement undo = new CakeMovement();
        undo.setBizDate(bizDate);
        undo.setCakeId(cakeId);
        undo.setCakeSize(cakeSize);
        undo.setDelta(+1);
        undo.setMoveType("UNPICK"); // 또는 PICKUP_CANCEL
        undo.setReservationId(reservationId);
        undo.setRequestId(requestId);
        cakeMovementMapper.insertMovement(undo);
    }

    /**
     * 4) toMakeMap 계산: demand(WAITING 예약 수요) - stock(현재 재고)
     * 결과: size -> flavor -> qty
     */
    @Transactional(readOnly = true)
    public Map<Integer, Map<Long, Integer>> calcToMakeMap(LocalDate today) {

        // 오늘 총 예약건 조회(pickUpStatus==WAITING)
        List<Reservation> demandRows = reservationService.countDemandByDate(today);

        // 오늘 만들어져 있는 케이크 조회
        List<CakeMovement> stockRows = cakeMovementMapper.sumStockByDate(today);

        // 케이크 테이블에서 케이크 id, 맛 조회
        List<Cake> cakeIdToFlavor = cakeService.getIdToFlavorMap();

        // (size->cakeId->demand)
        Map<Integer, Map<Long, Integer>> demandMap = new HashMap<>();
        for (Reservation row : demandRows) {
            demandMap.computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getCnt());
        }

        // (size->cakeId->stock) {사이즈 = {케이크아이디 = 재고, ...}}
        Map<Integer, Map<Long, Integer>> stockMap = new HashMap<>();
        for (CakeMovement row : stockRows) {
            stockMap.computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getStock());
        }

        // toMake
        Map<Integer, Map<Long, Integer>> toMakeResult = new HashMap<>();

        // demandMap : (size->cakeId->demand)
        for (Map.Entry<Integer, Map<Long, Integer>> reservations : demandMap.entrySet()) {
            int size = reservations.getKey();

            Map<Long, Integer> reservedCakeIdNAmount = reservations.getValue();

            // stockMap : (size->cakeId->stock)
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
        // 1) movements에서 날짜별 재고 집계 (cakeId+size 기준)
        List<CakeMovement> stockRows = cakeMovementMapper.sumStockByDate(bizDate);

        // 2) cakeId -> flavor 매핑 필요 (이미 cakes 테이블이 있으니 거기서 가져오기)
        List<Cake> flavorByCakeId = cakeService.getIdToFlavorMap();

        // 3) size -> (flavor -> stock) 형태로 변환 (UI가 flavor로 조회하니까)
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
