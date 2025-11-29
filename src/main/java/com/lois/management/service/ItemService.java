package com.lois.management.service;

import com.lois.management.domain.Item;
import com.lois.management.domain.StockRequest;
import com.lois.management.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {
    private final ItemMapper itemMapper;


    public List<Item> findAll() {
        return itemMapper.findAll();
    }

    public void create(Item req) {
        itemMapper.insert(req);
    }

    public List<Item> findAllOrderByPopularity() {
        return itemMapper.findAllOrderByPopularity();
    }

    public List<Item> findAllOrderByNameAsc() {
        return itemMapper.findAllOrderByNameAsc();
    }

    public List<Item> findByCategory(String category) {
        return itemMapper.findByCategory(category);
    }
}
