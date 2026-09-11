package com.lois.management.controller.demo;

import com.lois.management.domain.Item;
import com.lois.management.domain.StockRequest;
import com.lois.management.service.demo.DemoDataService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequestMapping("/demo/stock-requests")
@RequiredArgsConstructor
public class DemoStockRequestController {
    private final DemoDataService demoDataService;

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        addDashboardModel(session, model);
        return "demo/stock/request-dashboard";
    }

    @GetMapping("/{id}")
    public String detail(HttpSession session, @PathVariable("id") Long id, Model model) {
        StockRequest stockRequest = demoDataService.stockRequest(session, id)
                .orElseThrow(() -> new IllegalArgumentException("Demo stock request not found: " + id));
        model.addAttribute("stockRequest", stockRequest);
        return "demo/stock/fragments-request-detail :: requestDetail";
    }

    @PostMapping
    public ResponseEntity<Void> create(HttpSession session, @RequestParam("itemId") Long itemId) {
        boolean created = demoDataService.createStockRequest(session, itemId);
        if (!created) {
            return ResponseEntity.ok()
                    .header("HX-Trigger", "alreadyRequested")
                    .build();
        }
        return ResponseEntity.ok()
                .header("HX-Redirect", "/demo/stock-requests")
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpSession session, @PathVariable("id") Long id) {
        demoDataService.deleteStockRequest(session, id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/demo/stock-requests")
                .build();
    }

    @GetMapping("/print")
    public String print(HttpSession session, Model model) {
        model.addAttribute("stockRequests", demoDataService.stockRequests(session));
        model.addAttribute("printedAt", LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        return "demo/stock/stock-requests-print";
    }

    private void addDashboardModel(HttpSession session, Model model) {
        List<Item> top8Items = demoDataService.topItems(session);
        model.addAttribute("stockRequests", demoDataService.stockRequests(session));
        model.addAttribute("top8Items", top8Items);
        model.addAttribute("itemsExceptTop8", demoDataService.itemsExcept(session, top8Items));
    }
}
