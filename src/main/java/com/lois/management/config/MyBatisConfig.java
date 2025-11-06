package com.lois.management.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.lois.management.mapper")
public class MyBatisConfig {
}
