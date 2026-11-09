package com.lois.management.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthErrorController {

    @GetMapping("/403")
    public String forbidden() {
        return "error/403";
    }
}
