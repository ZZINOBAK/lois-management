package com.lois.management.global.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        basePackages = "com.lois.management",
        annotationClass = org.apache.ibatis.annotations.Mapper.class
)
public class MyBatisConfig {
}
