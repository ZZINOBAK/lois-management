package com.lois.management.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class MainController {

    @GetMapping("/")
    public String main() {
//        System.out.println("메인");
        log.info("메인 페이지 진입");
        return "index";
    }

}
