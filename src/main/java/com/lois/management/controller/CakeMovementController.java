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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

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

    @PostMapping("/on-site")
    public String sellOnSite(@RequestParam("cakeId") Long cakeId,
                             @RequestParam("cakeSize") Integer cakeSize,
                             @RequestParam(value = "amount", required = false) Integer amount,
                             @RequestParam(value = "note", required=false) String note) {

        LocalDate bizDate = LocalDate.now();
        String requestId = "POS-" + System.currentTimeMillis();
        Integer amountTest = 1;

        cakeMovementService.sellOnSite(bizDate, cakeId, cakeSize, amountTest, requestId, note);
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
