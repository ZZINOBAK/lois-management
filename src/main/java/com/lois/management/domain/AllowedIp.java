package com.lois.management.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AllowedIp {
    private Long id;
    private String ipAddress;
    private String name;
    private String contact;
    private String memo;
    private String status; // PENDING, APPROVED, REJECTED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
