package com.lois.management.auth;

import com.lois.management.domain.Employee;
import com.lois.management.dto.employee.EmployeeRequest;
import com.lois.management.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeService employeeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * API 로그인 (아이디/비밀번호 → JWT 발급)
     */
    public TokenResponse login(EmployeeRequest req) {


        Employee employee = employeeService.findByEmployeeName(req.getEmployeeName());
        if (employee == null) {
            throw new BadCredentialsException("사원명 또는 비밀번호가 올바르지 않습니다.");
        }

        // ✅ 비밀번호 검증 책임은 여기(AuthService)에 둔다
        if (!passwordEncoder.matches(req.getPassword(), employee.getPassword())) {
            throw new BadCredentialsException("사원명 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtTokenProvider.createToken(
                employee.getEmployeeName(),
                employee.getRole()
        );

        return new TokenResponse(token);
    }

    /**
     * 세션으로 이미 로그인된 사용자를 기준으로 JWT만 발급 ( /api/auth/token 용 )
     */
    public String issueTokenFor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("먼저 로그인하세요.");
        }

        String employeeName = authentication.getName();
        Employee employee = employeeService.findByEmployeeName(employeeName);
        if (employee == null) {
            throw new BadCredentialsException("직원 정보를 찾을 수 없습니다.");
        }

        return jwtTokenProvider.createToken(
                employee.getEmployeeName(),
                employee.getRole()
        );
    }

}
