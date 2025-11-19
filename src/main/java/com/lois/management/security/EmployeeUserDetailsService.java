package com.lois.management.security;

import com.lois.management.domain.Employee;
import com.lois.management.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeService employeeService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("loadUserByUsername username={}", username);
        Employee e = employeeService.findByEmployeeName(username);
        log.info("found employee password={}", e != null ? e.getPassword() : null);
        // username = 로그인 시 입력한 아이디
        Employee employee = employeeService.findByEmployeeName(username);
        // 만약 loginId로 찾는 거면 findByLoginId(username) 이런 식으로 변경

        if (employee == null) {
            throw new UsernameNotFoundException("직원을 찾을 수 없습니다: " + username);
        }

        return new EmployeeUserDetails(employee);    }
}
