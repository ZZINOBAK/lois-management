package com.lois.management.dto.ipaccess;

import lombok.Data;

@Data
public class AccessRequestForm {
    private String ipAddress;
    private String name;
    private String contact;
    private String memo;
}
