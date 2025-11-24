package com.lois.management.controller.api;

import com.lois.management.domain.dto.ReservationCreateReq;
import com.lois.management.dto.reservation.ReservationRes;
import com.lois.management.dto.reservation.ReservationSummaryRes;
import com.lois.management.dto.reservation.ReservationUpdateReq;
import com.lois.management.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservation API", description = "케이크 예약 CRUD")
public class ReservationApiController {
    private final ReservationService reservationService;

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

}
