package com.lois.management.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
public class Cake {
    private Long id;
    private String flavor;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    //reservation-dashboard line 140 용
    private String name;
    private String code;



}
