package com.lois.management.domain.reservation.service.limiter;

import java.time.LocalDate;
import java.time.LocalTime;

// 인터페이스 = 역할
public interface ReservationLimiter {
    boolean tryAcquireSlot(LocalDate resDate, LocalTime resTime);

}
