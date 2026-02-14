package com.lois.management.controller.api;

import com.lois.management.domain.Reservation;
import com.lois.management.domain.dto.ReservationCreateReq;
import com.lois.management.dto.reservation.ReservationRes;
import com.lois.management.dto.reservation.ReservationSummaryRes;
import com.lois.management.dto.reservation.ReservationUpdateReq;
import com.lois.management.service.CakeMovementService;
import com.lois.management.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/reservations")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Reservation API", description = "케이크 예약 CRUD")
public class ReservationApiController {
    private final ReservationService reservationService;
    private final CakeMovementService cakeMovementService;

    @Operation(summary = "예약 목록 조회")
    @GetMapping
    public List<ReservationSummaryRes> list() {
        return reservationService.findAllSummaries(); // 응답 DTO로 반환 권장
    }

    @Operation(summary = "예약 단건 조회")
    @GetMapping("/{id}")
    public ReservationRes get(@PathVariable("id") Long id) {
        return reservationService.findOne(id);
    }

    @Operation(summary = "예약 생성")
    @PostMapping
    public ResponseEntity<ReservationRes> create(@Valid @RequestBody ReservationCreateReq req) {
        Long id = reservationService.create(req);
        return ResponseEntity
                .created(URI.create("/api/reservations/" + id))
                .body(new ReservationRes(id, "CREATED"));
    }

    @Operation(summary = "예약 수정")
    @PutMapping("/{id}")
    public ReservationRes updateApi(@PathVariable("id") Long id,
                                 @Valid @RequestBody ReservationUpdateReq req) {
        reservationService.updateApi(id, req);
        return new ReservationRes(id, "UPDATED");
    }

    @Operation(summary = "예약 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        reservationService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/pickup-toggle") // 픽업 상태 토글
    public String togglePickup(@PathVariable("id") Long id, Model model, @RequestParam("rowNo") int rowNo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        log.info("[API] authType={} name={} authorities={}",
                auth.getClass().getSimpleName(),
                auth.getName(),
                auth.getAuthorities());

//        reservationService.togglePickupStatus(id);

        String requestId = "RES-" + System.currentTimeMillis();
        cakeMovementService.togglePickupReservation(id, requestId);

        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("rowNo", rowNo);

        // ✅ 픽업 버튼 fragment만 반환
//        return "reservation/reservation-dashboard :: pickupButton(r=${r})";
//        return "reservation/reservation-dashboard :: rowFragment(r=${r}, rowNo=${rowNo})";
        return "fragments/reservation-row :: rowFragment(r=${r}, rowNo=${rowNo})";
    }

    @PatchMapping("/{id}/make-toggle") // 제작 상태 토글
    public String toggleMake(@PathVariable("id") Long id, Model model) {
        reservationService.toggleMakeStatus(id);
        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);

        // ✅ 맛/제작 버튼 fragment만 반환
        return "reservation/dashboard :: makeButton(r=${r})";
    }
}
