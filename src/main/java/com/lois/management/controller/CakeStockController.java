package com.lois.management.controller;

import com.lois.management.service.CakeStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/cake-stocks")
@RequiredArgsConstructor
public class CakeStockController {
    private final CakeStockService cakeStockService;

    @PostMapping("/produce")
    public ResponseEntity<Void> produce(@RequestParam("cake_id") Long cakeId,
                                        @RequestParam("cake_size") Integer cakeSize) {
        if (cakeId <= 0 || (cakeSize != 1 && cakeSize != 2)) {
            //유효성 검사 실패
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cake ID or size");
        }
        cakeStockService.produce(cakeId, cakeSize);
        return ResponseEntity.noContent().build();
    }
}
