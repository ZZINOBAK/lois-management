package com.lois.management.controller.api;

import com.lois.management.domain.StockRequest;
import com.lois.management.service.StockRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api/stock-requests")
@Slf4j
@RequiredArgsConstructor
public class StockRequestApiController {

    private final StockRequestService stockRequestService;

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


}
