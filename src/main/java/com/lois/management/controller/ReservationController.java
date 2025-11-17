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

    @GetMapping
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

    @GetMapping("/sort")
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

    public List<Reservation> findAll() {
        return reservationService.findAll();
    }

    @GetMapping("/{id}")
    public Reservation findById(@PathVariable("id") Long id) {
        return reservationService.findById(id);
    }

    @GetMapping("/new")
    public String startReserve(Model model) {

        model.addAttribute("reserve", new Reservation());
        return "reservation/reserve";
    }

    @GetMapping("/step/{no}")
    public String step(@PathVariable("no") int no, Model model) {
        List<Cake> cakes = findAllCakeFlavor();
        model.addAttribute("cakes", cakes);

        model.addAttribute("stepNo", no);
        return "reservation/steps :: step" + no;
    }

    public List<Cake> findAllCakeFlavor() {
        return reservationService.findAllCakeFlavor();
    }

    @PostMapping("/step/1")
    public String submitStep1(@RequestParam("cakeId") Long cakeId,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setCakeId(cakeId);
        model.addAttribute("stepNo", 2);
        return "reservation/steps :: step2";
    }

    @PostMapping("/step/2")
    public String submitStep2(@RequestParam("date")  LocalDate date,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setResDate(date);
        model.addAttribute("stepNo", 3);
        return "reservation/steps :: step3";
    }

    @PostMapping("/step/3")
    public String submitStep3(@RequestParam("time") LocalTime time,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setResTime(time);
        model.addAttribute("stepNo", 4);
        return "reservation/steps :: step4";
    }

    @PostMapping("/step/4")
    public String submitStep4(@RequestParam("contact") String contact,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model) {
        reserve.setContact(contact);
        model.addAttribute("stepNo", 5);
        return "reservation/steps :: step5";
    }

    @PostMapping("/step/5")
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

    @PostMapping("/finish")
    public String finish(@ModelAttribute("reserve") Reservation reserve,
                         SessionStatus status,
                         RedirectAttributes redirect) {
        create(reserve);
        status.setComplete();
        redirect.addFlashAttribute("resvDone", true); // 완료 알림용 플래시
        return "redirect:/reservations";
    }

    @PostMapping
    public void create(Reservation reservation) {
        reservationService.create(reservation);
    }

    @PostMapping("/sample")
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

    @GetMapping("/{id}/edit")
    public String editReservation(@PathVariable("id") Long id, Model model) {
        model.addAttribute("reservation", reservationService.findById(id));
        return "reservation/edit-reservation";
    }

    @PatchMapping("/{id}")
    public void update1(@PathVariable("id") Long id) {
        reservationService.update1(id);
        // 지난번에 지피티랑 만든 Update 메소드 떄문에 일단 update1로 만듬. 추후 수정 예정
    }

    @PatchMapping("/{id}/picked-up")
    public void pickedUp(@PathVariable("id") Long id) {
        reservationService.pickedUp(id);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable("id") Long id, Model model) {
        reservationService.delete(id);
        model.addAttribute("reservations", reservationService.findAll());
        return "reservation/dashboard :: list"; // 리스트 fragment만 반환
    }

}
