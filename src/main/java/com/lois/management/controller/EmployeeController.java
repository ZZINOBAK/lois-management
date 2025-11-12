package com.lois.management.controller;

import com.lois.management.domain.Employee;
import com.lois.management.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public String showDashboard() {
        return "employee/dashboard";
    }

    @GetMapping("/new")
    public String showRegisterForm() {
        return "employee/register";
    }


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
