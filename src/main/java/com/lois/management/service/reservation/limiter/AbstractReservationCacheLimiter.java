package com.lois.management.service.reservation.limiter;

import java.time.LocalDate;
import java.util.Map;

public abstract class AbstractReservationCacheLimiter extends AbstractReservationLimiter implements ReservationCacheLimiter {
    protected abstract Map<String, ?> getDailyReservationCounter();

    protected abstract Map<String, ?> getHourlyReservationCounter();

    public final void initCounters(String dailyKey, String hourlyKey, int count) {
        doInitCounters(dailyKey, hourlyKey, count);
    }
    protected abstract void doInitCounters(String dailyKey, String hourlyKey, int count);

    public final void deletePastCounters(LocalDate today) {
        getDailyReservationCounter().keySet().removeIf(key -> {
            try {
                return LocalDate.parse(key).isBefore(today);
            } catch (Exception e) {
                return false;
            }
        });

        getHourlyReservationCounter().keySet().removeIf(key -> {
            try {
                String datePart = key.split(":")[0];
                return LocalDate.parse(datePart).isBefore(today);
            } catch (Exception e) {
                return false;
            }
        });
    }
}
