package com.lois.management.domain.supply.item.service;

import com.lois.management.domain.supply.item.Category;
import com.lois.management.domain.supply.item.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryMapper categoryMapper;
    public List<Category> findAll() {
        return categoryMapper.findAll();
    }
}
