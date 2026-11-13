package com.lois.management.domain.sms.dto;

import lombok.Data;

@Data
public class SmsDto {
    private String phoneNumber;
    private String text;
}
