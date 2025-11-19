package com.lois.management.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")   // 🔹 /api 가 베이스
@RequiredArgsConstructor
public class TestRoleApiController {

    // 👑 관리자 전용
    @GetMapping("/admin/ping")
    public Map<String, String> adminPing(Authentication auth) {
        return Map.of(
                "endpoint", "/api/admin/ping",
                "message", "관리자만 볼 수 있는 응답입니다.",
                "user", auth.getName()
        );
    }

    // 👥 로그인만 되어 있으면 모두 접근 가능
    @GetMapping("/user/ping")
    public Map<String, String> userPing(Authentication auth) {
        return Map.of(
                "endpoint", "/api/user/ping",
                "message", "로그인한 누구나 볼 수 있는 응답입니다.",
                "user", auth.getName()
        );
    }

}
