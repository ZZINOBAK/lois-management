package com.lois.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 매일 00:00 케이크 재고 초기화(CakeStockService)
public class LoisManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoisManagementApplication.class, args);
	}

}
