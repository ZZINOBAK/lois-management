package com.lois.management.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Getter
public class Employee {
    private Long id;
    private String employeeName;
    private String phone;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
