package com.lois.management.dto;

import lombok.Data;

@Data
public class SmsDto {
    private String phoneNumber;
    private String text;
}
