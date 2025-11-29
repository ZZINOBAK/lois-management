package com.lois.management.controller;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/reservations")
@RequiredArgsConstructor
@SessionAttributes("reserve")
@Slf4j
public class ReservationController {
    private final ReservationService reservationService;

    @GetMapping //케이크 예약 버튼
    public String showDashboard(Model model) {
        // 1) TRACE - 가장 상세한 내부 동작 (개발 중 흐름 확인용)
        log.trace("대시보드 조회 시작 - 내부 흐름(trace)");

        // 2) DEBUG - 디버깅용 상세 정보 (개발 단계에서 자주 사용)
        log.debug("컨트롤러 진입 - showDashboard 호출됨(debug)");

        // 3) INFO - 정상적인 '사건' 기록 (실제 운영 환경에서 남기는 로그)
        log.info("[GET /reservations] 예약 대시보드 조회 요청 받음(info)");

        List<Reservation> reservations = findAll();
        // 4) DEBUG - 비즈니스 결과에 대한 상세 정보
        log.debug("예약 조회 결과 size={}, firstItem={}, resDate={}",
                reservations.size(),
                reservations.isEmpty() ? "empty" : reservations.get(0).getId(),
                reservations.get(0).getResDate());

        // 5) WARN - 위험하거나 예상 못한 상황 (예: 데이터 없음)
        if (reservations.isEmpty()) {
            log.warn("예약 데이터가 0개입니다. 화면이 비어있을 수 있습니다.(warn)");
        }
        // 6) ERROR - 실제 오류 또는 치명적 문제
        try {
            model.addAttribute("reservations", reservations);
        } catch (Exception e) {
            log.error("모델에 데이터 추가 중 오류 발생(error). reservations={}", reservations, e);
            throw e; // 오류 재발생
        }

        if (true) {   // 강제 WARN
            log.warn("⚠ 테스트 WARN 로그입니다. (실제 오류 아님)");
        }

//        try {
//            throw new RuntimeException("테스트 ERROR 발생");
//        } catch (Exception e) {
//            log.error("❌ 테스트 ERROR 로그입니다.", e);
//        }
//        model.addAttribute("reservations", reservations);
        return "reservation/dashboard";
    }

    @GetMapping("/sort") //픽업 시간 순으로 정렬
    public String sortByPickUpTime(@RequestParam(name = "scope", defaultValue = "all") String scope,
                                   Model model) {

        List<Reservation> reservations;

        if ("today".equals(scope)) {
            // 오늘 날짜 + 시간 순 정렬
            reservations = reservationService.findTodayOrderByPickUpTime();
        } else {
            // 전체 + 시간 순 정렬
            reservations = reservationService.findAllOrderByPickUpTime();
        }

        model.addAttribute("reservations", reservations);

        // 🔥 list fragment만 리턴 (대시보드 템플릿의 th:fragment="list")
        return "reservation/dashboard :: list";
    }

    public List<Reservation> findAll() { //케이크 예약 전체 조회
        return reservationService.findAll();
    }

    @GetMapping("/{id}") //케이크 예약(id) 상세 조회
    public Reservation findById(@PathVariable("id") Long id) {
        return reservationService.findById(id);
    }

    @GetMapping("/new") //케이크 예약 - 예약하기 버튼
    public String startReserve(Model model) {

        model.addAttribute("reserve", new Reservation());
        return "reservation/reserve";
    }

    @GetMapping("/step/{no}") //케이크 예약 - 예약하기 버튼 클릭 후 첫 페이지 로딩
    public String step(@PathVariable("no") int no, Model model) {
        List<Cake> cakes = findAllCakeFlavor();
        model.addAttribute("cakes", cakes);

        model.addAttribute("stepNo", no);
        return "reservation/steps :: step" + no;
    }

    public List<Cake> findAllCakeFlavor() {
        return reservationService.findAllCakeFlavor();
    }

    @PostMapping("/step/1") //케이크 예약 - 맛 선택
    public String submitStep1(@RequestParam("cakeId") Long cakeId,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setCakeId(cakeId);
        model.addAttribute("stepNo", 2);
        return "reservation/steps :: step2";
    }

    @PostMapping("/step/2") //케이크 예약 - 날짜 선택
    public String submitStep2(@RequestParam("date")  LocalDate date,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setResDate(date);
        model.addAttribute("stepNo", 3);
        return "reservation/steps :: step3";
    }

    @PostMapping("/step/3") //케이크 예약 - 시간 선택
    public String submitStep3(@RequestParam("time") LocalTime time,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setResTime(time);
        model.addAttribute("stepNo", 4);
        return "reservation/steps :: step4";
    }

    @PostMapping("/step/4") //케이크 예약 - 연락처 입력
    public String submitStep4(@RequestParam("contact") String contact,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setContact(contact);
        model.addAttribute("stepNo", 5);
        return "reservation/steps :: step5";
    }

    @PostMapping("/step/5") //케이크 예약 - 예약정보 확인 후 확정
    public String submitStep5(@ModelAttribute("reserve") Reservation reserve,
                              Model model) {

        model.addAttribute("stepNo", 6);
        return "reservation/steps :: step6";
    }

//    @PostMapping("/finish")
//    public ResponseEntity<Void> finish(@ModelAttribute("reserve") Reservation reserve,
//                         SessionStatus status) {
//        create(reserve);
//        status.setComplete();                 // 세션 정리
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.add("HX-Redirect", "/reservations/dashboard"); // 쿼리로 완료표시
//        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
//    }

    @PostMapping("/finish") //케이크 예약 - 예약 완료
    public String finish(@ModelAttribute("reserve") Reservation reserve,
                         SessionStatus status,
                         RedirectAttributes redirect) {
        create(reserve);
        status.setComplete();
        redirect.addFlashAttribute("resvDone", true); // 완료 알림용 플래시
        return "redirect:/reservations";
    }

    @PostMapping //케이크 예약 DB 생성
    public void create(Reservation reservation) {
        reservationService.create(reservation);
    }

    @PostMapping("/sample") //케이크 예약 임의 DB 생성
    public String createSampleReservation() {

        Reservation r = new Reservation();
        r.setResDate(LocalDate.now());
        r.setResTime(LocalTime.of(19, 0)); // 오후 7시
        r.setCakeId(1L);                   // 샘플용 케이크 id (있던 거 하나)
        r.setCakeSize(2);
        r.setContact("010-0000-0000");
        r.setPaid(false);
        r.setNote("샘플 데이터");

        reservationService.create(r);      // 평소 쓰던 저장 메서드

        Reservation rr = new Reservation();
        rr.setResDate(LocalDate.now());
        rr.setResTime(LocalTime.of(17, 0)); // 오후 7시
        rr.setCakeId(1L);                   // 샘플용 케이크 id (있던 거 하나)
        rr.setCakeSize(2);
        rr.setContact("010-1111-1111");
        rr.setPaid(false);
        rr.setNote("샘플 데이터");

        reservationService.create(rr);      // 평소 쓰던 저장 메서드

        return "redirect:/reservations";
    }

    @GetMapping("/{id}/edit") //케이크 예약(id) 수정 버튼 - 수정 폼으로 이동
    public String editReservation(@PathVariable("id") Long id, Model model) {
        Reservation reservation = reservationService.findById(id);
        log.debug("수정 폼 날짜 resDate={}", reservation.getResDate());

        model.addAttribute("reservation", reservation);
        return "reservation/edit-reservation";
    }

    @PatchMapping("/{id}") //케이크 예약(id) 수정 DB 업데이트
    public String update(@PathVariable("id") Long id, @ModelAttribute Reservation reservation) {
        reservationService.update(id, reservation);
        return "redirect:/reservations";
    }

    @PatchMapping("/{id}/pickup-toggle") //케이크 예약(id) 픽업(완료) DB 업데이트
    public String togglePickup(@PathVariable("id") Long id, Model model) {
        reservationService.togglePickupStatus(id);
        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);

        log.debug("픽업 상태 pickupStatus={}", updated.getPickupStatus());
        log.debug("픽업 후 맛 cakeFlavor={}", updated.getCakeFlavor());

        // row.html 안의 rowFragment를 반환
        return "reservation/dashboard :: rowFragment(r=${r})";

    }


    @PatchMapping("/{id}/make-toggle") //케이크 예약(id) 제작(완료) DB 업데이트
    public String toggleMake(@PathVariable("id") Long id, Model model) {
        reservationService.toggleMakeStatus(id);
        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);
        log.debug("제작 상태 pickupStatus={}", updated.getMakeStatus());
        log.debug("제작 후 맛 cakeFlavor={}", updated.getCakeFlavor());

        // row.html 안의 rowFragment를 반환
        return "reservation/dashboard :: rowFragment(r=${r})";
    }


    @DeleteMapping("/{id}") //케이크 예약(id) 삭제 DB 업데이트
    public String delete(@PathVariable("id") Long id, Model model) {
        reservationService.delete(id);
        model.addAttribute("reservations", reservationService.findAll());
        return "reservation/dashboard :: list"; // 리스트 fragment만 반환
    }

}
