package com.lois.management.service;

import com.lois.management.domain.Category;
import com.lois.management.mapper.CategoryMapper;
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
