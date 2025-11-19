package com.lois.management.controller.api;

import com.lois.management.domain.Employee;
import com.lois.management.dto.auth.TokenResponse;
import com.lois.management.dto.common.ApiErrorResponse;
import com.lois.management.dto.employee.EmployeeRequest;
import com.lois.management.security.JwtProvider;
import com.lois.management.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final JwtProvider jwtProvider;
    private final EmployeeService employeeService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody EmployeeRequest req) {

        // 1. 유저 확인
        Employee employee = employeeService.findByEmployeeName(req.getEmployeeName());

        if (employee == null || !employeeService.matchesPassword(req.getPassword(), employee.getPassword())) {
            // ❌ 아이디 or 비번 틀림 → 401 + 에러 바디
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse("AUTH_001", "사원명 또는 비밀번호가 올바르지 않습니다."));
        }

        // 2. JWT 생성 (이건 네가 이미 잘 바꿔둔 부분)
        String token = jwtProvider.createToken(
                employee.getEmployeeName(),
                employee.getRole()
        );

        // 3. 응답 (토큰 + 유저 정보 같이 내려줌)
        TokenResponse res = new TokenResponse(
                token
        );

        return ResponseEntity.ok(res);
    }

    @GetMapping("/token") //세션+jwt
    public Map<String, String> issueToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "먼저 로그인하세요.");
        }

        String employeeName = authentication.getName();
        Employee employee = employeeService.findByEmployeeName(employeeName);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "직원 정보를 찾을 수 없습니다.");
        }

        String token = jwtProvider.createToken(
                employee.getEmployeeName(),
                employee.getRole()
        );

        return Map.of("token", token);
    }
}
