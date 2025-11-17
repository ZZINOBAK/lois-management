package com.lois.management.dto.employee;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class EmployeeRequest {
    private String employeeName;
    private String password;

}
