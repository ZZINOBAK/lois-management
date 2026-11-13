package com.lois.management.domain.employee.dto;

import lombok.Data;

@Data
public class EmployeeRequest {
    private String employeeName;
    private String password;

}