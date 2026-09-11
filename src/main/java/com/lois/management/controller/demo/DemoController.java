package com.lois.management.controller.demo;

import com.lois.management.service.demo.DemoDataService;
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
