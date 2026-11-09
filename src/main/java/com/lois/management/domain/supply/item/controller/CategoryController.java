package com.lois.management.domain.supply.item.controller;

import com.lois.management.domain.supply.item.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    private void findAll() {
        categoryService.findAll();
    }
}
