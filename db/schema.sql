
CREATE TABLE `allowed_ips` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ip_address` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `contact` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `memo` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `categories` (
  `id` int NOT NULL AUTO_INCREMENT,
  `category_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_categories_name` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `items` (
  `id` int NOT NULL AUTO_INCREMENT,
  `item_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `current_qty` int NOT NULL DEFAULT '0',
  `min_qty` int NOT NULL DEFAULT '0',
  `category_id` int NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_items_name` (`item_name`),
  KEY `fk_items_category_id` (`category_id`),
  CONSTRAINT `fk_items_category_id` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `stock_requests` (
  `id` int NOT NULL AUTO_INCREMENT,
  `item_id` int NOT NULL,
  `req_qty` int NOT NULL DEFAULT '0',
  `status` enum('REQUESTED','COMPLETED') COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'REQUESTED',
  `last_sms_at` datetime DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_requests_item` (`item_id`),
  CONSTRAINT `fk_order_requests_item_id` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `cakes` (
  `id` int NOT NULL AUTO_INCREMENT,
  `flavor` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `flavor_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `reservations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `res_date` date NOT NULL,
  `res_time` time NOT NULL,
  `cake_id` int NOT NULL,
  `cake_size` int DEFAULT '2',
  `candles` int DEFAULT '0',
  `contact` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `paid` tinyint(1) NOT NULL DEFAULT '0',
  `make_status` enum('RESERVED','READY') COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'RESERVED',
  `note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `picked_up_at` datetime DEFAULT NULL,
  `pickup_status` enum('WAITING','PICKED') COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'WAITING',
  `contact_suffix` varchar(4) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '전화번호 뒷자리 4자리',
  PRIMARY KEY (`id`),
  KEY `idx_res_date_time` (`res_date`,`res_time`),
  KEY `idx_reservations_cake_id` (`cake_id`),
  KEY `idx_reservations_contact_suffix` (`contact_suffix`),
  KEY `idx_reservations_contact` (`contact`),
  CONSTRAINT `reservations_cakes_FK` FOREIGN KEY (`cake_id`) REFERENCES `cakes` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `cake_movements` (
  `id` int NOT NULL AUTO_INCREMENT,
  `biz_date` date NOT NULL DEFAULT (curdate()),
  `cake_id` int NOT NULL,
  `cake_size` int NOT NULL,
  `delta` int NOT NULL,
  `move_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reservation_id` int DEFAULT NULL,
  `request_id` char(36) COLLATE utf8mb4_general_ci NOT NULL,
  `memo` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request` (`request_id`),
  KEY `idx_date` (`biz_date`),
  KEY `fk_moves_cake` (`cake_id`),
  KEY `fk_moves_res` (`reservation_id`),
  CONSTRAINT `fk_moves_cake` FOREIGN KEY (`cake_id`) REFERENCES `cakes` (`id`),
  CONSTRAINT `fk_moves_res` FOREIGN KEY (`reservation_id`) REFERENCES `reservations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `employees` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `employee_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `password` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `role` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `shifts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `employee_id` int unsigned NOT NULL,
  `work_date` date NOT NULL,
  `clock_in` time NOT NULL,
  `clock_out` time DEFAULT NULL,
  `hours_total` decimal(5,2) DEFAULT NULL,
  `open_key` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_shift_open` (`open_key`),
  KEY `idx_shift_emp_date` (`employee_id`,`work_date`),
  CONSTRAINT `fk_shifts_emp` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_shift_time` CHECK (((`clock_out` is null) or (`clock_out` > `clock_in`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `reservation_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '정책 PK',
  `daily_max_limit` int NOT NULL DEFAULT '-1' COMMENT '당일 최대 예약 제한 수량 (-1: 무제한)',
  `hourly_max_limit` int NOT NULL DEFAULT '10' COMMENT '시간당 최대 예약 제한 수량',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '설정 수정 일시',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='예약 제한 정책 테이블';

INSERT INTO reservation_policies (daily_max_limit, hourly_max_limit)
VALUES (-1, 10);