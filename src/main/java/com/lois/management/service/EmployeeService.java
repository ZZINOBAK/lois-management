package com.lois.management.service;

import com.lois.management.domain.Employee;
import com.lois.management.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final PasswordEncoder passwordEncoder;
    private final EmployeeMapper employeeMapper;

    public void create(Employee employee) {

        employee.setPassword(passwordEncoder.encode(employee.getPassword())); // 🔥 암호화 저장

        employeeMapper.insert(employee);
    }

    public List<Employee> findAll() {
        return employeeMapper.findAll();
    }


    public Employee findByEmployeeName(String employeeName) {
        return employeeMapper.findByEmployeeName(employeeName);
    }

    public boolean matchesPassword(String password, String password1) {
        return password.equals(password1);
    }


}
