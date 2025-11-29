package com.lois.management.controller;

import com.lois.management.domain.Category;
import com.lois.management.domain.Item;
import com.lois.management.service.CategoryService;
import com.lois.management.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/items")
@Slf4j
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final CategoryService categoryService;


    @GetMapping
    public String showDashboard(Model model) {
        List<Item> items = findAll();
        List<Category> categories = categoryService.findAll();
        log.debug("상품 전체 조회={}", items.get(2));
        model.addAttribute("items", items);
        model.addAttribute("categories", categories);
        return "item/dashboard";
    }

    public List<Item> findAll() {
        return itemService.findAll();
    }

    @GetMapping(params = "category")
    public String findAllByCategory(@RequestParam(value = "category", required = false) String category, Model model) {
        // 방어 코드 (null 체크)
        if (category == null || category.isBlank()) {
            category = "전체";
        }

        // ✅ 1) "전체"일 때 → TOP8 + 나머지 구조 그대로 만들기
        if ("전체".equals(category)) {
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

            model.addAttribute("top8Items", top8Items);
            model.addAttribute("itemsExceptTop8", itemsExceptTop8);

            // 🔥 전체용 fragment (TOP8 + 나머지)
            return "stock/fragments-item-grid :: itemGridAll";
        }

        // ✅ 2) 카테고리별일 때 → 해당 카테고리만 가나다로
        List<Item> items = itemService.findByCategory(category);
        model.addAttribute("items", items);

        // 🔥 카테고리용 fragment (단일 리스트)
        return "stock/fragments-item-grid :: itemGridCategory";
    }


    @PostMapping
    public String create(Item item, @RequestParam("imageFile")MultipartFile file) {
        itemService.create(item);
        return "redirect:/items";

    }

}
