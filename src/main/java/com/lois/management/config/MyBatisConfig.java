package com.lois.management.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.lois.management.mapper") // 경로에 있는 모든 Mapper Interface 빈 등록
public class MyBatisConfig {
}
