package com.lois.management.service.reservation.limiter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public interface ReservationCacheLimiter {

    void initCounters(String dailyKey, String hourlyKey, int count);

    void updatePolicy(int dailyMaxLimit, int hourlyMaxLimit);
    void clearCounters();

    void decreaseCounters(String dailyKey, String hourlyKey);
    void deletePastCounters(LocalDate today);
    void deletePastSlot(LocalDate today);

    void openSlot(String key);

}
