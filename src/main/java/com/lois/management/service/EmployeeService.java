package com.lois.management.service;

import com.lois.management.domain.Employee;
import com.lois.management.mapper.EmployeeMapper;
import com.lois.management.mapper.EmployeeSocialAccountsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeMapper employeeMapper;
    private final EmployeeSocialAccountsMapper employeeSocialAccountsMapper;

    public void create(Employee employee) {
        employeeMapper.insert(employee);
    }

    public List<Employee> findAll() {
        return employeeMapper.findAll();
    }

    public Employee findByEmployeeName(String employeeName) {
        return employeeMapper.findByEmployeeName(employeeName);
    }

    public Employee findById(Long id) {
        return employeeMapper.findById(id);
    }
    @Transactional
    public Long createByOAuth2(String employeeName, String email, String role, String provider, String providerUserId) {
        Employee employee = new Employee();

        employee.setEmployeeName(employeeName);
        employee.setRole(role);
        create(employee);

        employeeSocialAccountsMapper.insert(employee.getId(), provider, providerUserId, email);
        return findByOauth2Id(provider, providerUserId);
    }

    public Long findByOauth2Id(String provider, String providerUserId) {
        return employeeSocialAccountsMapper.findByProviderNProviderUserId(provider, providerUserId);
    }
}
