package com.lois.management.controller;

import com.lois.management.domain.Cake;
import com.lois.management.domain.Reservation;
import com.lois.management.service.ReservationService;
import lombok.RequiredArgsConstructor;
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
@SessionAttributes("reserve")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    @GetMapping
    public String showDashboard(Model model) {
        List<Reservation> reservations = findAll();
        model.addAttribute("reservations", reservations);
        return "reservation/dashboard";
    }

    private List<Reservation> findAll() {
        return reservationService.findAll();
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

    private List<Cake> findAllCakeFlavor() {
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
}
