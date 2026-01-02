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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CakeMovementService {

    private final CakeMovementMapper movementMapper;

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

        movementMapper.insertMovement(m);
    }

    /**
     * 2) 현장판매 -N
     * movements-only라서 "현재 재고"를 SUM으로 확인하고 부족하면 막는다.
     * (동시성 100% 방어는 cache 버전에서 더 완벽해짐)
     */
    @Transactional
    public void sellOnSite(LocalDate bizDate, Long cakeId, Integer cakeSize, int amount, String requestId, String memo) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");

        int stock = movementMapper.getStockByKey(bizDate, cakeId, cakeSize);
        if (stock < amount) {
            throw new IllegalStateException("재고 부족: 현재=" + stock + ", 요청=" + amount);
        }

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(-amount);
        m.setMoveType("ON_SITE");
        m.setRequestId(requestId);
        m.setMemo(memo);

        movementMapper.insertMovement(m);
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

        int stock = movementMapper.getStockByKey(bizDate, cakeId, cakeSize);
        if (stock < 1) {
            // 현실적으로 "재고 0인데 픽업 처리"는 데이터 꼬임이므로 막는 게 안전
            throw new IllegalStateException("재고 부족(픽업 처리 불가). cakeId=" + cakeId + ", size=" + cakeSize);
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

        movementMapper.insertMovement(m);
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
            int stock = movementMapper.getStockByKey(bizDate, cakeId, cakeSize);
            if (stock < 1) {
                throw new IllegalStateException("재고 부족(픽업 처리 불가). cakeId=" + cakeId + ", size=" + cakeSize);
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
            movementMapper.insertMovement(m);

            return;
        }

        // PICKED -> WAITING : 취소(원복) => 재고 +1
        reservationService.updatePickupStatus(reservationId, "WAITING", null);

        CakeMovement undo = new CakeMovement();
        undo.setBizDate(bizDate);
        undo.setCakeId(cakeId);
        undo.setCakeSize(cakeSize);
        undo.setDelta(+1);
        undo.setMoveType("UNPICK"); // 또는 PICKUP_CANCEL
        undo.setReservationId(reservationId);
        undo.setRequestId(requestId);
        movementMapper.insertMovement(undo);
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
        List<CakeMovement> stockRows = movementMapper.sumStockByDate(today);

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
        Map<Integer, Map<Long, Integer>> result = new HashMap<>();

        // demandMap : (size->cakeId->demand)
        for (Map.Entry<Integer, Map<Long, Integer>> sizeEntry : demandMap.entrySet()) {
            int size = sizeEntry.getKey();

            Map<Long, Integer> dByCake = sizeEntry.getValue();

            // stockMap : (size->cakeId->stock)
            Map<Long, Integer> sByCake = stockMap.getOrDefault(size, Map.of());

            Map<Long, Integer> byCakeId = new HashMap<>();

            for (Map.Entry<Long, Integer> e : dByCake.entrySet()) {
                Long cakeId = e.getKey();
                int demand = e.getValue();

                int stock = sByCake.getOrDefault(cakeId, 0);
                int toMake = Math.max(demand - stock, 0);

                byCakeId.put(cakeId, toMake);
            }
            result.put(size, byCakeId);
        }

        log.info("[toMake] result={}", result);
        return result;
    }

    public Map<Integer, Map<Long, Integer>> calcStockMap(LocalDate bizDate) {
        // 1) movements에서 날짜별 재고 집계 (cakeId+size 기준)
        List<CakeMovement> stockRows = movementMapper.sumStockByDate(bizDate);

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
