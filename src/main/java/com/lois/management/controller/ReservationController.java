package com.lois.management.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.service.CakeService;
import com.lois.management.service.ReservationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reservations")
@RequiredArgsConstructor
@SessionAttributes("reserve")
@Slf4j
public class ReservationController {
    private final ReservationService reservationService;
    private final CakeService cakeService;

    @GetMapping //케이크 예약 버튼
    public String showDashboard(Model model) {
        log.info("[GET /reservations] 예약 대시보드 조회 요청 받음(info)");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        List<Reservation> reservations = findAll();
        log.debug("예약 조회 결과 size={}", reservations.size());

        // ✅ 집계용: 무조건 오늘
        List<Reservation> todayReservations =
                reservationService.findTodayForToMakeCalc(today);

        Map<Integer, Map<String, Integer>> toMakeMap =
                reservationService.calcToMakeBySizeAndFlavor(todayReservations);



        if (reservations.isEmpty()) {
            log.warn("예약 데이터가 0개입니다. 화면이 비어있을 수 있습니다.(warn)");
        }

        try {
            model.addAttribute("reservations", reservations);
            model.addAttribute("cakeSizes", List.of(2, 1));
            // ✅ 한글 맛 -> CSS 클래스 매핑 (색 유지용)
            Map<String, String> flavorCss = new LinkedHashMap<>();
            flavorCss.put("가나슈", "mk-ganache");
            flavorCss.put("모카", "mk-moka");
            flavorCss.put("바닐라", "mk-vanilla");
            flavorCss.put("레몬", "mk-lemon");
            flavorCss.put("딸기", "mk-strawberry");
            flavorCss.put("초코딸기", "mk-choco-strawberry");
            flavorCss.put("티라미슈", "mk-tiramisu");
            flavorCss.put("바스크", "mk-basque");
            flavorCss.put("커스텀", "mk-custom");

            model.addAttribute("flavorCss", flavorCss);
// ✅ 화면 출력 순서도 여기서 고정 (DB 순서랑 달라져도 안전)
            model.addAttribute("flavors", new ArrayList<>(flavorCss.keySet()));
            model.addAttribute("toMakeMap", toMakeMap);
        } catch (Exception e) {
            log.error("모델에 데이터 추가 중 오류 발생(error). reservations={}", reservations, e);
            throw e; // 오류 재발생
        }
        // 오늘 날짜 추가
        model.addAttribute("today", LocalDate.now());
        return "reservation/reservation-dashboard";
    }

    @GetMapping("/list") // 필터 조회
    public String sortByPickUpTime(@RequestParam(name = "range", defaultValue = "all") String range,
                                   @RequestParam(name = "sort", required = false) String sort,
                                   @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   Model model) {

        List<Reservation> reservations = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 1) sort 기본값 (없으면 null)
        String s = (sort == null) ? "" : sort.trim().toLowerCase();

        // 2) sort + range 조합 처리
        switch (s) {
            case "created-at": // 등록순(예: 최신등록순)
                if ("today".equals(range)) {
                    reservations = reservationService.findTodayOrderByCreatedAtDesc(today);
                } else if ("from-today".equals(range)) {
                    reservations = reservationService.findFromTodayOrderByCreatedAtDesc();
                } else if ("date".equals(range) && date != null) {
                    reservations = reservationService.findByDateOrderByCreatedAtDesc(date);
                }
                break;

            case "waiting": // pickupStatus=WAITING만
                if ("today".equals(range)) {
                    reservations = reservationService.findTodayByPickupStatusWaiting(today);
                } else {
                    reservations = reservationService.findByPickupStatus("WAITING");
                }
                break;

            default:
                // sort 없거나 알 수 없는 값: 범위만 적용 (기본 정렬은 서비스에서 결정)
                if ("today".equals(range)) {
                    // 오늘 날짜 + 시간 순 정렬
                    reservations = reservationService.findTodayOrderByPickUpTime(today);
                } else if ("from-today".equals(range)) {
                    // 전체 + 시간 순 정렬 / 오늘부터 전체조회
                    reservations = reservationService.findFromTodayOrderByPickUpTime();
                } else if ("date".equals(range) && date != null) {
                    // 특정 날짜 예약 + 시간 오름차순
                    reservations = reservationService.findByDateOrderByPickUpTime(date);
                } else {
                    reservations = reservationService.findAll();
                }
        }

        LocalDate targetDate = null;
        if ("today".equals(range)) {
            targetDate = LocalDate.now();
        } else if ("date".equals(range) && date != null) {
            targetDate = date;
        }

        model.addAttribute("reservations", reservations);

        // ✅ 집계는 무조건 오늘
        List<Reservation> todayReservations =
                reservationService.findTodayForToMakeCalc(today);

        Map<Integer, Map<String, Integer>> toMakeMap =
                reservationService.calcToMakeBySizeAndFlavor(todayReservations);

        model.addAttribute("cakeSizes", List.of(2, 1));
        // ✅ 한글 맛 -> CSS 클래스 매핑 (색 유지용)
        Map<String, String> flavorCss = new LinkedHashMap<>();
        flavorCss.put("가나슈", "mk-ganache");
        flavorCss.put("모카", "mk-moka");
        flavorCss.put("바닐라", "mk-vanilla");
        flavorCss.put("레몬", "mk-lemon");
        flavorCss.put("딸기", "mk-strawberry");
        flavorCss.put("초코딸기", "mk-choco-strawberry");
        flavorCss.put("티라미슈", "mk-tiramisu");
        flavorCss.put("바스크", "mk-basque");
        flavorCss.put("커스텀", "mk-custom");

        model.addAttribute("flavorCss", flavorCss);
// ✅ 화면 출력 순서도 여기서 고정 (DB 순서랑 달라져도 안전)
        model.addAttribute("flavors", new ArrayList<>(flavorCss.keySet()));
        model.addAttribute("toMakeMap", toMakeMap);

        model.addAttribute("today", LocalDate.now());
        model.addAttribute("range", range);
        model.addAttribute("date", targetDate);         // ✅ 추가 (프린트용)


        // list fragment만 리턴 (대시보드 템플릿의 th:fragment="list")
        return "reservation/reservation-dashboard :: list";
    }

    public List<Reservation> findAll() { //케이크 예약 전체 조회
        return reservationService.findAll();
    }

    @GetMapping("/{id}") //케이크 예약(id) 상세 조회
    public Reservation findById(@PathVariable("id") Long id) {
        return reservationService.findById(id);
    }

    @GetMapping("/search") //번호로 예약 검색
    public String findByContact(@RequestParam("contactSuffix") String contactSuffix, Model model) {

        List<Reservation> reservations = reservationService.findByContactSuffix(contactSuffix);


        model.addAttribute("reservations", reservations);

        // ✅ showDashboard랑 똑같이 today도 내려주기
        model.addAttribute("today", LocalDate.now());

        // 🔥 list fragment만 리턴 (대시보드 템플릿의 th:fragment="list")
        return "reservation/reservation-dashboard :: list";
    }

    @GetMapping("/filter")
    public String filterByPickupStatus(@RequestParam("pickupStatus") String pickupStatus,
                                       Model model) {
        List<Reservation> reservations = reservationService.findByPickupStatus(pickupStatus);
        model.addAttribute("reservations", reservations);
        model.addAttribute("today", LocalDate.now());
        return "reservation/reservation-dashboard :: list";
    }

//    @GetMapping("/print")
//    public String printTodayReservations(Model model) {
//
//        LocalDate today = LocalDate.now();
//
//        // ✅ 오늘 예약 + 픽업 시간 오름차순 정렬
//        List<Reservation> reservations = reservationService.findTodayOrderByPickUpTime();
//
//        model.addAttribute("reservations", reservations);
//        model.addAttribute("today", today);
//
//        // 프린트 전용 템플릿
//        return "reservation/print";
//    }

    @GetMapping("/print")
    public String printReservations(
            @RequestParam(name = "range", defaultValue = "today") String range,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model
    ) {

        // ✅ 출력 대상 날짜 결정
        LocalDate targetDate;
        if ("date".equals(range) && date != null) {
            targetDate = date;
        } else {
            targetDate = LocalDate.now();
        }

        // ✅ 대상 날짜 예약 + 픽업시간 오름차순
        List<Reservation> reservations =
                reservationService.findByDateOrderByPickUpTime(targetDate);

        model.addAttribute("reservations", reservations);
        model.addAttribute("today", targetDate);  // print.html에서 today로 찍고 있으니 targetDate를 넣어줌
        model.addAttribute("range", range);
        model.addAttribute("date", targetDate);

        return "reservation/print";
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

//    @PostMapping("/step/2") //케이크 예약 - 날짜 선택
//    public String submitStep2(@RequestParam("date")  LocalDate date,
//                              @ModelAttribute("reserve") Reservation reserve,
//                              Model model) {
//        reserve.setResDate(date);
//        model.addAttribute("stepNo", 3);
//        return "reservation/steps :: step3";
//    }

    @PostMapping("/step/2") // 케이크 예약 - 날짜 선택, 12월 5일 수정
    public String submitStep2(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @ModelAttribute("reserve") Reservation reserve,
            Model model
    ) {
        LocalDate today = LocalDate.now();
        LocalDate max   = today.plusMonths(3);   // 최대 3개월 후까지

        // 유효성 검사: 오늘 이전, 최대일 이후, 일요일(휴무)인 경우
        if (date.isBefore(today)
                || date.isAfter(max)
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {

            // 스텝2로 다시 돌려보내기
            model.addAttribute("stepNo", 2);
            model.addAttribute("errorMessage", "예약이 불가능한 날짜입니다. 다시 선택해주세요.");

            // 날짜 선택 화면(스텝2) 조각 다시 렌더링
            return "reservation/steps :: step2";
        }

        // 통과하면 예약 객체에 날짜 세팅
        reserve.setResDate(date);

        // 다음 스텝 번호 세팅
        model.addAttribute("stepNo", 3);

        // 스텝3 조각 반환
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
                              @RequestParam(value = "force", defaultValue = "false") boolean force,
                              @ModelAttribute("reserve") Reservation reserve,
                              Model model,
                              HttpServletResponse response) throws JsonProcessingException {
        reserve.setContact(contact);

        // 2) 완전 동일 예약(연락처 + 픽업일시 + 케이크맛4개) 존재하면 "취소" (step5로 못 감)
        if (reservationService.existsExactSameReservation(reserve)) {
            response.setHeader("HX-Trigger", "{\"lois:alert\":{\"code\":\"DUP_EXACT\"}}");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
            return null; // HTMX는 헤더만 처리, DOM 교체 안 함
        }

        // 3) 연락처만 동일한 예약이 이미 있으면 "확인(confirm)" 요구 (force=false일 때만)
        if (!force && reservationService.existsByContact(contact)) {
            response.setHeader("HX-Trigger", "{\"lois:confirm\":{\"code\":\"DUP_CONTACT\"}}");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
            return null;
        }

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

    @PostMapping("/on-site") //케이크 예약 DB 생성
    public String createOnSite(Reservation reservation) {
        reservationService.createOnSite(reservation);
        return "redirect:/reservations";
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
        return "redirect:/reservations#row-" + id;
    }

    @PatchMapping("/{id}/pickup-toggle") // 픽업 상태 토글
    public String togglePickup(@PathVariable("id") Long id, Model model) {
        reservationService.togglePickupStatus(id);
        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);

        // ✅ 픽업 버튼 fragment만 반환
        return "reservation/reservation-dashboard :: pickupButton(r=${r})";
    }

    @PatchMapping("/{id}/make-toggle") // 제작 상태 토글
    public String toggleMake(@PathVariable("id") Long id, Model model) {
        reservationService.toggleMakeStatus(id);
        Reservation updated = reservationService.findById(id);
        model.addAttribute("r", updated);

        // ✅ 맛/제작 버튼 fragment만 반환
        return "reservation/reservation-dashboard :: makeButton(r=${r})";
    }

    @DeleteMapping("/{id}") //케이크 예약(id) 삭제 DB 업데이트
    public String delete(@PathVariable("id") Long id, Model model) {
        reservationService.delete(id);
        model.addAttribute("reservations", reservationService.findAll());
        return "reservation/reservation-dashboard :: list"; // 리스트 fragment만 반환
    }

    @GetMapping("/on-site") // 현장 판매
    public String onSite(Model model) {
        List<Cake> cakes = findAllCakeFlavor();
        model.addAttribute("cakes", cakes);
        model.addAttribute("reserve", new Reservation());
        return "reservation/on-site";
    }
}
