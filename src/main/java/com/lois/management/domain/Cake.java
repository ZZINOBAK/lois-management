package com.lois.management.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Setter
@Getter
public class Cake {
    private Long id;
    private String flavor;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
