package com.lois.management.service;

import com.lois.management.domain.Employee;
import com.lois.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeServiceWRepo {
    private final EmployeeRepository employeeRepository;

    public void create(Employee employee) {
        employeeRepository.create(employee);
    }

    public List<Employee> findAll() {
        return employeeRepository.findAll();
    }

    public Employee findById(Long id) {
        return null;
    }

    public int update(Employee employee) {
        return 0;
    }

    public int delete(Long id) {
        return 0;
    }
}
