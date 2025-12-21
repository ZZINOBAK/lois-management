package com.lois.management.auth;

import com.lois.management.dto.employee.EmployeeRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthApiController {
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;



    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody EmployeeRequest req) {
        try {
            TokenResponse res = authService.login(req);
            return ResponseEntity.ok(res);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("AUTH_001 사원명 또는 비밀번호가 올바르지 않습니다.");


        }
    }

//    @GetMapping("/token") // 세션 + JWT 발급
//    public Map<String, String> issueToken(Authentication authentication) {
//        // ✅ 여기서도 실제 발급 로직은 AuthService에게 맡김
//        String token = authService.issueTokenFor(authentication);
//        return Map.of("token", token);
//    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> issueToken(
            HttpSession session,
            Authentication authentication
    ) {
        log.info("!!!token 컨터롤러 진입!!");


        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }


        // 세션당 1회 발급(재호출 시 같은 토큰 반환)
        String existing = (String) session.getAttribute("LOIS_ACCESS_TOKEN");
        log.info("로이스액세스 토큰 확인 {}", existing);
        if (existing != null) {
            if (jwtTokenProvider.isValid(existing)) {
                return ResponseEntity.ok(Map.of("accessToken", existing));
            }
            session.removeAttribute("LOIS_ACCESS_TOKEN"); // 만료면 제거
        }

        log.info("여기");

        EmployeeUserDetails principal = (EmployeeUserDetails) authentication.getPrincipal();
        String username = principal.getEmployee().getEmployeeName();
        String role = principal.getEmployee().getRole();
        log.info("여기222222");

        String token = jwtTokenProvider.createToken(username, role);

        log.info("!!!토큰 생성 완료!! {}", token);
        session.setAttribute("LOIS_ACCESS_TOKEN", token);
        return ResponseEntity.ok(Map.of("accessToken", token));
    }
}
