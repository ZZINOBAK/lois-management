package com.lois.management.controller;

import com.lois.management.dto.production.CakeProductionNeedDto;
import com.lois.management.service.CakeProductionService;
import com.lois.management.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequestMapping("/cake-production")
@RequiredArgsConstructor
public class CakeProductionController {
    private final ReservationService reservationService;
    private final CakeProductionService cakeProductionService;

    @GetMapping // 제작 관리 페이지 이동
    public String showProductionPage(Model model) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // 케이크 종류 조회
        model.addAttribute("cakes", reservationService.findAllCakeFlavor());
        // 오늘 예약 조회
        model.addAttribute("reservations", reservationService.findTodayOrderByPickUpTime(today));
        // 제작 필요 수량 조회
        model.addAttribute("productionNeeds", cakeProductionService.findTodayProductionNeeds());
        return "cake/production/production-management";
    }

    @GetMapping("/production-needs") // 스톡 증감 시 제작 필요 수량 재조회
    public ResponseEntity<List<CakeProductionNeedDto>> showProductionNeeds() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(cakeProductionService.findTodayProductionNeeds());
    }
}
