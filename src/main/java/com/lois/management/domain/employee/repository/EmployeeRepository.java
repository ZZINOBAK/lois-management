package com.lois.management.domain.employee.repository;

import com.lois.management.domain.employee.Employee;
import com.lois.management.domain.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EmployeeRepository {
    private final EmployeeMapper employeeMapper;

    public void create(Employee employee) {
        employeeMapper.insert(employee);
    }

    public List<Employee> findAll() {
        return employeeMapper.findAll();
    }


}
