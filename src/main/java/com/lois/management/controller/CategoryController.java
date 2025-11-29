package com.lois.management.controller;

import com.lois.management.service.CategoryService;
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
