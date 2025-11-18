package com.lois.management.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestApiController {
    @GetMapping
    public String hello(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return "hello " + username;
    }
}
