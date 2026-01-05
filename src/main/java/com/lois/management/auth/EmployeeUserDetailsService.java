package com.lois.management.auth;

import com.lois.management.domain.Employee;
import com.lois.management.service.EmployeeService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeUserDetailsService implements UserDetailsService {
    private final EmployeeService employeeService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {


        // username = 로그인 시 입력한 아이디
        Employee employee = employeeService.findByEmployeeName(username);
        // 만약 loginId로 찾는 거면 findByLoginId(username) 이런 식으로 변경
        log.info("found employee password={}", employee != null ? employee.getPassword() : null);

        if (employee == null) {
            throw new UsernameNotFoundException("직원을 찾을 수 없습니다: " + username);
        }

        return new EmployeeUserDetails(employee);
    }

}
