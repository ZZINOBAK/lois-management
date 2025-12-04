package com.lois.management.controller;


import com.lois.management.domain.Item;
import com.lois.management.domain.StockRequest;
import com.lois.management.service.ItemService;
import com.lois.management.service.StockRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock-requests")
@RequiredArgsConstructor
@Slf4j
public class StockRequestController {

    private final StockRequestService stockRequestService;
    private final ItemService itemService;

    @GetMapping
    public String showDashboard(Model model) {
        List<StockRequest> stockRequests = findAll();
        List<Item> itemsByPopularity = itemService.findAllOrderByPopularity();

        List<Item> top8Items = itemsByPopularity.stream()
                .limit(8)
                .toList();


        List<Item> itemsByNameAsc = itemService.findAllOrderByNameAsc();
        Set<Long> top8Ids = top8Items.stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        List<Item> itemsExceptTop8 = itemsByNameAsc.stream()
                .filter(item -> !top8Ids.contains(item.getId()))
                .toList();

        log.debug("top8Items={}", top8Items.size());
//        log.debug("요청 기간={}", stockRequests.get(0).getDaysSinceRequest());
        model.addAttribute("stockRequests", stockRequests);
        model.addAttribute("top8Items", top8Items);
        model.addAttribute("itemsExceptTop8", itemsExceptTop8);
        return "stock/request-dashboard";
    }

    public List<StockRequest> findAll() {
        return stockRequestService.findAll();
    }

    @GetMapping("/{id}")
    public String findById(@PathVariable("id") Long id, Model model) {
        StockRequest stockRequest = stockRequestService.findById(id);
        log.debug("주문 요청 상세 정보={}", stockRequest.getCreatedAt());

        model.addAttribute("stockRequest", stockRequest);
        return "stock/fragments-request-detail :: requestDetail";
    }


    @PostMapping
    public ResponseEntity<Void> create(@RequestParam("itemId") Long itemId) {
        log.debug("itemId={}", itemId);

        StockRequest stockRequest = new StockRequest();
        stockRequest.setItemId(itemId);
        boolean created = stockRequestService.create(stockRequest);

        if (!created) {
            log.debug("재고 주문 요청 생성 실패 : 중복됨");

            // 이미 존재할 경우 사용자에게 alert 띄움
            return ResponseEntity.ok()
                    .header("HX-Trigger", "alreadyRequested")
                    .build();
        }
        log.debug("재고 주문 요청 생성 완료");

        return ResponseEntity.ok()
                .header("HX-Redirect", "/stock-requests")
                .build();
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        stockRequestService.delete(id);
        log.debug("재고 주문 요청 삭제 완료");
        return ResponseEntity.ok()
                .header("HX-Redirect", "/stock-requests")
                .build();
    }
}
