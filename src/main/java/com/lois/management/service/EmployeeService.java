package com.lois.management.service;

import com.lois.management.domain.Employee;
import com.lois.management.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeMapper employeeMapper;

    public void create(Employee employee) {
        employeeMapper.insert(employee);
    }

    public List<Employee> findAll() {
        return employeeMapper.findAll();
    }
}
