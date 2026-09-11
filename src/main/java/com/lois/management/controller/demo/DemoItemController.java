package com.lois.management.controller.demo;

import com.lois.management.domain.Item;
import com.lois.management.service.demo.DemoDataService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/demo/items")
@RequiredArgsConstructor
public class DemoItemController {
    private final DemoDataService demoDataService;

    @GetMapping(params = "category")
    public String findAllByCategory(HttpSession session,
                                    @RequestParam(value = "category", required = false) String category,
                                    Model model) {
        String selected = category == null || category.isBlank() ? "전체" : category;
        if ("전체".equals(selected)) {
            List<Item> top8Items = demoDataService.topItems(session);
            model.addAttribute("top8Items", top8Items);
            model.addAttribute("itemsExceptTop8", demoDataService.itemsExcept(session, top8Items));
            return "demo/stock/fragments-item-grid :: itemGridAll";
        }
        model.addAttribute("items", demoDataService.itemsByCategory(session, selected));
        return "demo/stock/fragments-item-grid :: itemGridCategory";
    }
}
