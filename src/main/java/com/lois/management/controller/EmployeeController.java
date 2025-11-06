package com.lois.management.controller;

import com.lois.management.domain.Employee;
import com.lois.management.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/findAll")
    public List<Employee> findAll() {
        return employeeService.findAll();
    }


    @PostMapping
    public String create(@RequestBody String req) {
        Employee employee = new Employee();
        employee.setEmployeeName(req);
        employeeService.create(employee);
        return "index";

    }

}
