package com.lois.management.controller.api;

import com.lois.management.domain.Employee;
import com.lois.management.dto.common.ApiErrorResponse;
import com.lois.management.dto.employee.EmployeeResponse;
import com.lois.management.service.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeApiController {
    private final EmployeeService employeeService;

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest req) {
        String username = (String) req.getAttribute("username");

        if (username == null) {
            // JWT 없음 or 검증 실패
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse("AUTH_002", "인증이 필요합니다."));
        }

        Employee employee = employeeService.findByEmployeeName(username);
        if (employee == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse("EMP_001", "직원 정보를 찾을 수 없습니다."));
        }

        return ResponseEntity.ok(new EmployeeResponse(employee.getPhone()));
    }

    @GetMapping("/admin-only")
    public ResponseEntity<?> adminOnly(HttpServletRequest req) {
        String username = (String) req.getAttribute("username");
        String role = (String) req.getAttribute("role");

        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse("AUTH_002", "인증이 필요합니다."));
        }

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiErrorResponse("AUTH_003", "관리자만 접근 가능합니다."));
        }

        return ResponseEntity.ok("관리자 기능 실행!");
    }

}
