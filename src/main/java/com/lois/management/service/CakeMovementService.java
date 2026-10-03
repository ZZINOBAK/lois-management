package com.lois.management.service;

import com.lois.management.domain.Cake;
import com.lois.management.domain.CakeMovement;
import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ProductionStatus;
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
    public void toggleProduce(Reservation reservation, String requestId, String note) {

        CakeMovement m = new CakeMovement();
        m.setBizDate(reservation.getResDate());
        m.setCakeId(reservation.getCakeId());
        m.setCakeSize(reservation.getCakeSize());
        m.setMemo(note);
        m.setDelta(1);
        m.setMoveType("PRODUCED");
        m.setRequestId(requestId);
        m.setReservationId(reservation.getId());
        log.info("제작 완료 후 케이크무브먼트 데이터 추가 = {}", m);
        cakeMovementMapper.insertMovement(m);

        log.info("제작완료 : {} ", cakeMovementMapper.findById(m.getId()));
    }

    @Transactional
    public void pageProduce(Reservation reservation, String requestId, String note) {
        LocalDate bizDate = LocalDate.now(ZoneId.of("Asia/Seoul"));

        Reservation targetReservation = reservationMapper.findReservationIdForProduce(bizDate, reservation.getCakeId(), reservation.getCakeSize());
        if (targetReservation != null) {
            //오늘 예약 건 중 make_status가 RESERVED인 예약 중 가장 예약 시간이 빠른 건 한개의 make_status를 READY로 변경
            reservationMapper.updateWithProduce(targetReservation.getId());
            reservation.setResDate(targetReservation.getResDate());
            reservation.setId(targetReservation.getId());
        } else {
            reservation.setResDate(bizDate);
            log.info("getReservationId = {}", reservation.getId());
        }


        CakeMovement m = new CakeMovement();
        m.setBizDate(reservation.getResDate());
        m.setCakeId(reservation.getCakeId());
        m.setCakeSize(reservation.getCakeSize());
        m.setMemo(note);
        m.setDelta(1);
        m.setMoveType("PRODUCED");
        m.setRequestId(requestId);
        m.setReservationId(reservation.getId());
        log.info("제작 완료 후 케이크무브먼트 데이터 추가 = {}", m);
        cakeMovementMapper.insertMovement(m);

        log.info("제작완료 : {} ", cakeMovementMapper.findById(m.getId()));
    }

    @Transactional
    public void adjust(Reservation reservation, String requestId, String memo) {

        CakeMovement m = new CakeMovement();
        m.setBizDate(reservation.getResDate());
        m.setCakeId(reservation.getCakeId());
        m.setCakeSize(reservation.getCakeSize());
        m.setRequestId(requestId);
        m.setMemo(memo);

        m.setDelta(-1);
        m.setMoveType("UNDO_PRODUCED");
        m.setReservationId(reservation.getId());
        log.info("제작 완료 취소(원복) 후 케이크무브먼트 데이터 추가 = {}", m);
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
        Reservation reservation = new Reservation();
        reservation.setCakeId(cakeId);
        reservation.setCakeSize(cakeSize);
        reservation.setResDate(bizDate);

        CakeMovement m = new CakeMovement();
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(-1);
        m.setMoveType("PICKED");
        m.setRequestId(requestId);

        int stock = cakeMovementMapper.getStockByKey(bizDate, cakeId, cakeSize);
        int extraStock = cakeMovementMapper.getExtraStockByKey(bizDate, cakeId, cakeSize);

        if (stock < 1) {
            toggleProduce(reservation, requestId + "-1", "AUTO_PRODUCE_ON_SITE");
            m.setMemo(note);
        } else if (extraStock > 0) {
            m.setMemo(note + " 여분 재고 사용");
        } else {
            Long reservationId = reservationService.readyToReserved(cakeId, cakeSize, bizDate);
            m.setReservationId(reservationId);
            m.setMemo(note + "예약 번호" + reservationId + " 제작상태 READY -> RESERVED");
        }
        log.info("현장 판매 후 케이크무브먼트 데이터 추가 = {}", m);
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

        if (!isPicked) { // 픽업예정인 예약 건에 대한 로직
            // WAITING -> PICKED : 재고 -1
            // 날짜, 호수, 맛 3가지로 만들어진 케이크 개수 확인
            int stock = cakeMovementMapper.getStockByKey(bizDate, cakeId, cakeSize);
            // 날짜, 호수, 맛 3가지로 예약 건 조회 및 예약건 별 제작 상태(PRODUCED/UNDO_PRODUCED) 체크
            List<Long> rIdsProduced = cakeMovementMapper.getReservationIdsByProduced(bizDate, cakeId, cakeSize);

            // 만들어진 케이크가 없거나, 현재 픽업 요청이 들어온 예약건이 조회되지 않는다면
            if (stock < 1 || !rIdsProduced.contains(reservationId)) {
                // 제작 완료 버튼을 클릭 안한 것으로 간주하고 해당 예약건의 제작 상태를 제작 완료로 변경
                reservationService.toggleMakeStatus(reservationId);
                log.info("픽업으로 인한 makeStatus 상태 변경 : RESERVED -> READY");
                // 그리고 케이크무브먼트 테이블에도 생산 기록 추가
                toggleProduce(r, requestId + "-1", "AUTO_PRODUCE_ON_PICKUP");
            }
            // 해당 예약건의 픽업 상태를 픽업 완료로 변경
            reservationService.updatePickupStatus(reservationId, "PICKED", LocalDateTime.now());

            // 그리고 케이크무브먼트 테이블에도 픽업 기록 추가
            m.setBizDate(bizDate);
            m.setCakeId(cakeId);
            m.setCakeSize(cakeSize);
            m.setDelta(-1);
            m.setMoveType("PICKED");
            m.setReservationId(reservationId);
            m.setRequestId(requestId);
            log.info("픽업 완료 후 케이크무브먼트 데이터 추가 = {}", m);
            cakeMovementMapper.insertMovement(m);

            return;
        }

        // 픽업 완료 건에 대한 로직
        // PICKED -> WAITING : 취소(원복) => 재고 +1
        // 해당 예약건의 픽업 상태를 픽업 대기로 변경(실수로 픽업 완료 시 원복하기 위한 기능)
        reservationService.updatePickupStatus(reservationId, "WAITING", LocalDateTime.now());

        // 그리고 케이크무브먼트 테이블에도 픽업 취소 기록 추가
        m.setBizDate(bizDate);
        m.setCakeId(cakeId);
        m.setCakeSize(cakeSize);
        m.setDelta(1);
        m.setMoveType("UNDO_PICKED");
        m.setReservationId(reservationId);
        m.setRequestId(requestId);
        log.info("픽업 완료 취소(원복) 후 케이크무브먼트 데이터 추가 = {}", m);
        cakeMovementMapper.insertMovement(m);
    }


    public ProductionStatus getProductionStatus(LocalDate bizDate) {

        Map<Integer, Map<Long, Integer>> demand = calcDemandMap(bizDate);
        Map<Integer, Map<Long, Integer>> produced = calcProducedMap(bizDate);
        Map<Integer, Map<Long, Integer>> pickupReady = calcPickupReadyMap(bizDate);
        Map<Integer, Map<Long, Integer>> toMake = calcToMakeMap(bizDate);
        Map<Integer, Map<Long, Integer>> extraStock = calcExtraStockMap(bizDate);

        log.info("[productionStatus] demand={}", demand);
        log.info("[productionStatus] produced={}", produced);
        log.info("[productionStatus] pickupReady={}", pickupReady);
        log.info("[productionStatus] toMake={}", toMake);
        log.info("[productionStatus] extraStock={}", extraStock);

        return new ProductionStatus(
                demand,
                produced,
                pickupReady,
                toMake,
                extraStock
        );

//        return new ProductionStatus(
//                calcDemandMap(bizDate),
//                calcProducedMap(bizDate),
//                calcPickupReadyMap(bizDate),
//                calcToMakeMap(bizDate),
//                calcExtraStockMap(bizDate)
//        );
    }

    // 1 오늘 예약 수량
    public Map<Integer, Map<Long, Integer>> calcDemandMap(LocalDate bizDate) {
        // 오늘 총 예약건 조회(pickUpStatus==WAITING)
        List<Reservation> demandRows = reservationService.countDemandByDate(bizDate);

        // 오늘 총 예약 건 : <size,<cakeId,demand>>
        Map<Integer, Map<Long, Integer>> demandMap = new HashMap<>();
        for (Reservation row : demandRows) {
            demandMap.computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getCnt());
        }

        return demandMap;
    }

    // 2 오늘 제작 완료 수량(예약건 + 여분 + 등)
    public Map<Integer, Map<Long, Integer>> calcProducedMap(LocalDate bizDate) {

        List<CakeMovement> producedRows =
                cakeMovementMapper.sumProducedByDate(bizDate);

        Map<Integer, Map<Long, Integer>> producedMap = new HashMap<>();

        for (CakeMovement row : producedRows) {
            producedMap
                    .computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getStock());
        }

        return producedMap;
    }

    // 3 제작 완료 + 아직 픽업되지 않은 예약
    public Map<Integer, Map<Long, Integer>> calcPickupReadyMap(LocalDate bizDate) {

        List<CakeMovement> rows =
                cakeMovementMapper.sumPickupReadyByDate(bizDate);

        Map<Integer, Map<Long, Integer>> pickupReadyMap = new HashMap<>();

        for (CakeMovement row : rows) {
            pickupReadyMap
                    .computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getStock());
        }

        return pickupReadyMap;
    }

    // 3-1 제작 완료 + 아직 픽업되지 않은 예약 + 여분
    public Map<Integer, Map<Long, Integer>> calcStockMap(LocalDate bizDate) {
        // SUM(delta) : sumStockByDate
        List<CakeMovement> stockRows = cakeMovementMapper.sumStockByDate(bizDate);

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

    // 4 아직 만들어야 하는 예약
    @Transactional(readOnly = true)
    public Map<Integer, Map<Long, Integer>> calcToMakeMap(LocalDate bizDate) {
        // 오늘 총 예약건 조회(pickUpStatus==WAITING)
        Map<Integer, Map<Long, Integer>> demandMap = calcDemandMap(bizDate);

        // 오늘 만들어져 있는 케이크 조회 : sumStockByDate(SUM(delta))
        List<CakeMovement> stockRows = cakeMovementMapper.sumStockByDate(bizDate);

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

    // 5 예약 없이 판매 가능한 여분
    public Map<Integer, Map<Long, Integer>> calcExtraStockMap(LocalDate bizDate) {

        List<CakeMovement> rows =
                cakeMovementMapper.sumExtraStockByDate(bizDate);

        Map<Integer, Map<Long, Integer>> extraStockMap = new HashMap<>();

        for (CakeMovement row : rows) {
            extraStockMap
                    .computeIfAbsent(row.getCakeSize(), k -> new HashMap<>())
                    .put(row.getCakeId(), row.getStock());
        }

        return extraStockMap;
    }





}
