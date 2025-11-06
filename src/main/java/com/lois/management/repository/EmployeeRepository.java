package com.lois.management.repository;

import com.lois.management.domain.Employee;
import com.lois.management.mapper.EmployeeMapper;
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
