package com.lois.management.controller.demo;

import com.lois.management.domain.Reservation;
import com.lois.management.service.demo.DemoDataService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequestMapping("/demo/reservations")
@RequiredArgsConstructor
@SessionAttributes("demoReserve")
public class DemoReservationController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DemoDataService demoDataService;

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        addDashboardModel(session, model, "all", null, null);
        return "demo/reservation/reservation-dashboard";
    }

    @GetMapping("/list")
    public String list(HttpSession session,
                       @RequestParam(name = "range", defaultValue = "all") String range,
                       @RequestParam(name = "sort", required = false) String sort,
                       @RequestParam(name = "date", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {
        addDashboardModel(session, model, range, sort, date);
        return "demo/reservation/reservation-dashboard :: list";
    }

    @GetMapping("/search")
    public String search(HttpSession session,
                         @RequestParam("contactSuffix") String contactSuffix,
                         Model model) {
        model.addAttribute("reservations", demoDataService.searchReservations(session, contactSuffix));
        model.addAttribute("today", LocalDate.now(KST));
        return "demo/reservation/reservation-dashboard :: list";
    }

    @GetMapping("/filter")
    public String filter(HttpSession session,
                         @RequestParam("pickupStatus") String pickupStatus,
                         Model model) {
        String sort = "PICKED".equals(pickupStatus) ? null : "waiting";
        addDashboardModel(session, model, "all", sort, null);
        return "demo/reservation/reservation-dashboard :: list";
    }

    @GetMapping("/print")
    public String print(HttpSession session,
                        @RequestParam(name = "range", defaultValue = "today") String range,
                        @RequestParam(name = "date", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        Model model) {
        LocalDate targetDate = "date".equals(range) && date != null ? date : LocalDate.now(KST);
        model.addAttribute("reservations", demoDataService.findReservations(session, "date", null, targetDate));
        model.addAttribute("today", targetDate);
        return "demo/reservation/print";
    }

    @GetMapping("/new")
    public String startReserve(Model model) {
        model.addAttribute("demoReserve", new Reservation());
        return "demo/reservation/reserve";
    }

    @GetMapping("/step/{no}")
    public String step(HttpSession session, @PathVariable("no") int no, Model model) {
        model.addAttribute("cakes", demoDataService.cakes(session));
        model.addAttribute("stepNo", no);
        return "demo/reservation/steps :: step" + no;
    }

    @PostMapping("/step/1")
    public String submitStep1(@RequestParam("cakeId") Long cakeId,
                              @ModelAttribute("demoReserve") Reservation reserve,
                              Model model) {
        reserve.setCakeId(cakeId);
        model.addAttribute("stepNo", 2);
        return "demo/reservation/steps :: step2";
    }

    @PostMapping("/step/2")
    public String submitStep2(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @ModelAttribute("demoReserve") Reservation reserve,
            Model model
    ) {
        LocalDate today = LocalDate.now(KST);
        LocalDate max = today.plusMonths(3);

        if (date.isBefore(today)
                || date.isAfter(max)
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            model.addAttribute("stepNo", 2);
            model.addAttribute("errorMessage", "예약이 불가능한 날짜입니다. 다시 선택해주세요.");
            return "demo/reservation/steps :: step2";
        }

        reserve.setResDate(date);
        model.addAttribute("stepNo", 3);
        return "demo/reservation/steps :: step3";
    }

    @PostMapping("/step/3")
    public String submitStep3(@RequestParam("time") LocalTime time,
                              @ModelAttribute("demoReserve") Reservation reserve,
                              Model model) {
        reserve.setResTime(time);
        model.addAttribute("stepNo", 4);
        return "demo/reservation/steps :: step4";
    }

    @PostMapping("/step/4")
    public String submitStep4(HttpSession session,
                              @RequestParam("contact") String contact,
                              @RequestParam(value = "force", defaultValue = "false") boolean force,
                              @ModelAttribute("demoReserve") Reservation reserve,
                              Model model,
                              HttpServletResponse response) {
        reserve.setContact(contact);

        if (demoDataService.existsExactSameReservation(session, reserve)) {
            response.setHeader("HX-Trigger", "{\"lois:alert\":{\"code\":\"DUP_EXACT\"}}");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        }

        if (!force && demoDataService.existsByContact(session, contact)) {
            response.setHeader("HX-Trigger", "{\"lois:confirm\":{\"code\":\"DUP_CONTACT\"}}");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        }

        model.addAttribute("stepNo", 5);
        return "demo/reservation/steps :: step5";
    }

    @PostMapping("/step/5")
    public String submitStep5(@ModelAttribute("demoReserve") Reservation reserve,
                              Model model) {
        model.addAttribute("stepNo", 6);
        return "demo/reservation/steps :: step6";
    }

    @PostMapping("/finish")
    public String finish(HttpSession session,
                         @ModelAttribute("demoReserve") Reservation reserve,
                         SessionStatus status,
                         RedirectAttributes redirect) {
        demoDataService.createReservation(session, reserve);
        status.setComplete();
        redirect.addFlashAttribute("resvDone", true);
        return "redirect:/demo/reservations";
    }

    @GetMapping("/simple-reservation")
    public String simpleReservation(HttpSession session, Model model) {
        model.addAttribute("cakes", demoDataService.cakes(session));
        model.addAttribute("reserve", new Reservation());
        return "demo/reservation/simple-reservation";
    }

    @PostMapping("/simple-reservation")
    public String simpleReservationInsert(HttpSession session,
                                          @RequestParam("cakeId") Long cakeId,
                                          @RequestParam("cakeSize") Integer cakeSize,
                                          @RequestParam("pickupTime") LocalTime pickupTime,
                                          @RequestParam("contactSuffix") String contact,
                                          @RequestParam("paid") Boolean paid,
                                          @RequestParam(value = "sameDay", defaultValue = "false") boolean sameDay,
                                          @RequestParam(value = "note", required = false) String note) {
        Reservation reservation = new Reservation();
        reservation.setCakeId(cakeId);
        reservation.setCakeSize(cakeSize);
        reservation.setResTime(pickupTime);
        reservation.setResDate(LocalDate.now(KST));
        reservation.setContact((sameDay ? "당일-0000-" : "010-0000-") + contact);
        reservation.setPaid(paid);
        reservation.setNote(note);
        demoDataService.createReservation(session, reservation);
        return "redirect:/demo/reservations";
    }

    @GetMapping("/produce")
    public String produce(HttpSession session, Model model) {
        model.addAttribute("cakes", demoDataService.cakes(session));
        model.addAttribute("reserve", new Reservation());
        return "demo/reservation/produce";
    }

    @PostMapping("/produce")
    public String produceSubmit(HttpSession session,
                                @RequestParam("cakeId") Long cakeId,
                                @RequestParam("cakeSize") Integer cakeSize,
                                @RequestParam(value = "note", required = false) String note) {
        demoDataService.produce(session, cakeId, cakeSize, note);
        return "redirect:/demo/reservations";
    }

    @GetMapping("/on-site")
    public String onSite(HttpSession session, Model model) {
        model.addAttribute("cakes", demoDataService.cakes(session));
        model.addAttribute("reserve", new Reservation());
        return "demo/reservation/on-site";
    }

    @PostMapping("/on-site")
    public String onSiteSubmit(HttpSession session,
                               @RequestParam("cakeId") Long cakeId,
                               @RequestParam("cakeSize") Integer cakeSize,
                               @RequestParam(value = "note", required = false) String note) {
        demoDataService.sellOnSite(session, cakeId, cakeSize, note);
        return "redirect:/demo/reservations";
    }

    @GetMapping("/{id}/edit")
    public String edit(HttpSession session, @PathVariable("id") Long id, Model model) {
        Reservation reservation = demoDataService.findReservation(session, id)
                .orElseThrow(() -> new IllegalArgumentException("Demo reservation not found: " + id));
        model.addAttribute("reservation", reservation);
        model.addAttribute("cakes", demoDataService.cakes(session));
        return "demo/reservation/edit-reservation";
    }

    @PatchMapping("/{id}")
    public String update(HttpSession session,
                         @PathVariable("id") Long id,
                         @ModelAttribute Reservation reservation) {
        demoDataService.updateReservation(session, id, reservation);
        return "redirect:/demo/reservations#row-" + id;
    }

    @PatchMapping("/{id}/pickup-toggle")
    public String togglePickup(HttpSession session,
                               @PathVariable("id") Long id,
                               @RequestParam(name = "rowNo", defaultValue = "1") int rowNo,
                               Model model) {
        Reservation reservation = demoDataService.togglePickup(session, id)
                .orElseThrow(() -> new IllegalArgumentException("Demo reservation not found: " + id));
        model.addAttribute("r", reservation);
        model.addAttribute("rowNo", rowNo);
        model.addAttribute("today", LocalDate.now(KST));
        return "demo/fragments/reservation-row :: rowFragment(r=${r}, rowNo=${rowNo})";
    }

    @PatchMapping("/{id}/make-toggle")
    public String toggleMake(HttpSession session,
                             @PathVariable("id") Long id,
                             @RequestParam(name = "rowNo", defaultValue = "1") int rowNo,
                             Model model) {
        Reservation reservation = demoDataService.toggleMake(session, id)
                .orElseThrow(() -> new IllegalArgumentException("Demo reservation not found: " + id));
        model.addAttribute("r", reservation);
        model.addAttribute("rowNo", rowNo);
        model.addAttribute("today", LocalDate.now(KST));
        return "demo/fragments/reservation-row :: rowFragment(r=${r}, rowNo=${rowNo})";
    }

    @DeleteMapping("/{id}")
    public String delete(HttpSession session, @PathVariable("id") Long id, Model model) {
        demoDataService.deleteReservation(session, id);
        addDashboardModel(session, model, "all", null, null);
        return "demo/reservation/reservation-dashboard :: list";
    }

    private void addDashboardModel(HttpSession session, Model model, String range, String sort, LocalDate date) {
        LocalDate today = LocalDate.now(KST);
        LocalDate targetDate = "today".equals(range) ? today : date;
        model.addAttribute("reservations", demoDataService.findReservations(session, range, sort, date));
        model.addAttribute("cakeSizes", List.of(2, 1));
        model.addAttribute("flavors", demoDataService.cakes(session));
        model.addAttribute("toMakeMap", demoDataService.toMakeMap(session));
        model.addAttribute("stockMap", demoDataService.stockMap(session));
        model.addAttribute("today", today);
        model.addAttribute("range", range);
        model.addAttribute("date", targetDate);
        model.addAttribute("printedAt", LocalDateTime.now(KST));
    }
}
