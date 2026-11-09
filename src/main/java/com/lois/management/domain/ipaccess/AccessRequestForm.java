package com.lois.management.domain.ipaccess;

import lombok.Data;

@Data
public class AccessRequestForm {
    private String ipAddress;
    private String name;
    private String contact;
    private String memo;
}
