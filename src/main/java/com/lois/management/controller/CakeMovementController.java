package com.lois.management.controller;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.service.CakeMovementService;
import com.lois.management.service.CakeService;
import com.lois.management.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cake-movements")
@RequiredArgsConstructor
@Slf4j
public class CakeMovementController {
    private final ReservationService reservationService;
    private final CakeMovementService cakeMovementService;
    private final CakeService cakeService;

    @PostMapping("/produce")
    public String produce(@RequestParam("cakeId") Long cakeId,
                          @RequestParam("cakeSize") Integer cakeSize,
                          @RequestParam(value = "amount", required = false) Integer amount,
                          @RequestParam(value = "note", required=false) String note) {

        LocalDate bizDate = LocalDate.now(); // 매장 영업일 기준이면 별도 계산
        String requestId = "WEB-" + System.currentTimeMillis();
        Integer amountTest = 1;

        cakeMovementService.produce(bizDate, cakeId, cakeSize, amountTest, requestId, note);

        return "redirect:/reservations";
    }

    @PostMapping("/{id}/produce")
    public String produceFromReservation(@PathVariable("id") Long id, Model model) {
//        LocalDate bizDate = LocalDate.now(); // 매장 영업일 기준이면 별도 계산
//        String requestId = "WEB-" + System.currentTimeMillis();

        LocalDate bizDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String requestId = "RES-PROD-" + id + "-" + System.currentTimeMillis();

        // 1) 예약 조회 (cakeId, cakeSize 필요)
        Reservation r = reservationService.findById(id); // 없으면 구현
        Long cakeId = r.getCakeId();
        Integer cakeSize = r.getCakeSize();

        cakeMovementService.produce(bizDate, cakeId, cakeSize, 1, requestId, "from reservation list");

        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);


        // ✅ 맛/제작 버튼 fragment만 반환
        return "reservation/reservation-dashboard :: makeButton(r=${r})";
    }

    @PatchMapping("/{id}/produce-toggle")
    public String produceFromReservationPatch(@PathVariable("id") Long id, Model model, @RequestParam("rowNo") int rowNo) {
        // 1) 예약 조회 (현재 makeStatus가 무엇인지가 토글 기준)
        Reservation r = reservationService.findById(id);

        Long cakeId = r.getCakeId();
        int cakeSize = r.getCakeSize(); // primitive int이면 int로 받기
        reservationService.toggleMakeStatus(id);

        boolean isReady = "READY".equals(r.getMakeStatus()); // enum이면 r.getMakeStatus()==MakeStatus.READY

        if (!isReady) {
            // ✅ 제작 완료 처리: +1 movement + makeStatus=READY
            String requestId = "RES-PROD-" + id + "-" + System.currentTimeMillis();

            cakeMovementService.produce(r.getResDate(), cakeId, cakeSize, 1, requestId, "from reservation list");

        } else {
            // ✅ 제작 취소 처리: 정책(가장 늦은 READY만 취소 가능) + -1 movement + makeStatus=RESERVED
            String requestId = "RES-UNDO-" + id + "-" + System.currentTimeMillis();
            cakeMovementService.adjust(r.getResDate(), cakeId, cakeSize, -1, requestId, "undo from reservation list");
        }

        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("rowNo", rowNo);


//        return "reservation/reservation-dashboard :: rowFragment(r=${r})";
        return "fragments/reservation-row :: rowFragment(r=${r}, rowNo=${rowNo})";

    }



    @PostMapping("/on-site")
    public String sellOnSite(@RequestParam("cakeId") Long cakeId,
                             @RequestParam("cakeSize") Integer cakeSize,
                             @RequestParam(value = "amount", required = false) Integer amount,
                             @RequestParam(value = "note", required=false) String note) {

        LocalDate today = LocalDate.now();
        String requestId = "POS-" + System.currentTimeMillis();
        Integer amountTest = 1;
        reservationService.readyToReserved(cakeId, cakeSize, today);

        cakeMovementService.sellOnSite(today, cakeId, cakeSize, amountTest, requestId, note);
        return "redirect:/reservations";
    }

    @GetMapping("/produce") // 현장 판매
    public String onSite(Model model) {
        List<Cake> cakes = findAllCakeFlavor();
        model.addAttribute("cakes", cakes);
        model.addAttribute("reserve", new Reservation());
        return "cake-movement/produce";
    }
    public List<Cake> findAllCakeFlavor() {
        return reservationService.findAllCakeFlavor();
    }

}
