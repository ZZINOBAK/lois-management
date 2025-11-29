package com.lois.management.service;

import com.lois.management.domain.Item;
import com.lois.management.domain.StockRequest;
import com.lois.management.mapper.StockRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockRequestService {
    private final StockRequestMapper stockRequestMapper;


    public List<StockRequest> findAll() {
        List<StockRequest> requests = stockRequestMapper.findAll();
        LocalDate today = LocalDate.now();

        return requests.stream()
                .map(sr -> {
                    StockRequest dto = new StockRequest();
                    dto.setId(sr.getId());
                    dto.setItemName(sr.getItemName());
                    dto.setCreatedAt(sr.getCreatedAt());

                    long days = ChronoUnit.DAYS.between(
                            sr.getCreatedAt().toLocalDate(),
                            today
                    );
                    dto.setDaysSinceRequest(days);

                    return dto;
                })
                .toList();
    }

    public StockRequest findById(Long id) {
        return stockRequestMapper.findById(id);
    }

    public boolean create(StockRequest req) {
        int check = stockRequestMapper.existsRequestedByItemId(req);
        if (check == 1) {
            return false;
        } else {
            stockRequestMapper.insert(req);
            return true;
        }
    }

    public void delete(Long id) {
        stockRequestMapper.delete(id);
    }


}
