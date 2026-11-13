package com.lois.management.domain.demo.controller;

import com.lois.management.domain.demo.service.DemoDataService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DemoController {
    private final DemoDataService demoDataService;

    @GetMapping("/demo")
    public String demo(HttpSession session) {
        demoDataService.data(session);
        return "demo/index";
    }
}
