package com.lois.management.service.reservation.limiter;

import java.time.LocalDate;
import java.time.LocalTime;

// 인터페이스 = 역할
public interface ReservationLimiter {
    boolean tryAcquireSlot(LocalDate resDate, LocalTime resTime);

}
