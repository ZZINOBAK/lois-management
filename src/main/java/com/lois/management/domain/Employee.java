package com.lois.management.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Setter
@Getter
public class Employee {
    private Long id;
    private String employeeName;
    private String phone;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
