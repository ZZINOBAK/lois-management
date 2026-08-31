package com.lois.management.service.reservation.limiter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//@Component("atomicConcurrencyGuard")
@RequiredArgsConstructor
@Getter
public class AtomicReservationLimiter extends AbstractReservationCacheLimiter {

//    private final ReservationMapper reservationMapper;
//    private final ReservationPolicyMapper reservationPolicyMapper;

    //    private final Map<String, Integer> policyCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> dailyReservationCounter = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> hourlyReservationCounter = new ConcurrentHashMap<>();

    private int dailyMaxLimit;
    private int hourlyMaxLimit;

//    @PostConstruct
//    public void initCache() {
//        ReservationPolicy policy = reservationPolicyMapper.selectLatestPolicy();
//        if (policy != null) {
//            policyCache.put("hourlyMax", policy.getHourlyMaxLimit());
//            policyCache.put("dailyMax", policy.getDailyMaxLimit());
//        }
//    }

    public void updatePolicy(int dailyMaxLimit, int hourlyMaxLimit) {
        this.dailyMaxLimit = dailyMaxLimit;
        this.hourlyMaxLimit = hourlyMaxLimit;
    }

    @Override
    public void clearCounters() {
        dailyReservationCounter.clear();
        hourlyReservationCounter.clear();

        System.out.println("🧹 Local Cache Counter 초기화 완료");

    }

    @Override
    public void decreaseCounters(String dailyKey, String hourlyKey) {
        // 1. [데일리 차감] .merge()를 쓰면 자물쇠 없이도 안전하게 예약 한 건 차감. AtomicInteger용
        getDailyReservationCounter().computeIfPresent(dailyKey, (k, v) -> {
            if (v.get() > 0) v.decrementAndGet();
            return v;
        });

        // 2. [시간대 차감] 동일하게 안전하게 예약 한 건 차감. AtomicInteger용
        getHourlyReservationCounter().computeIfPresent(hourlyKey, (k, v) -> {
            if (v.get() > 0) v.decrementAndGet();
            return v;
        });
    }

    @Override
    public boolean doTryAcquireSlot(LocalDate resDate, LocalTime resTime) {
//        if (!tryCheckFlag(resDate, resTime)) return false;

        String dailyCacheKey = resDate.toString();
        String hourlyCacheKey = resDate.toString() + ":" + resTime.getHour();
//        int hourlyMaxLimit = policyCache.getOrDefault("hourlyMax", 10);
//        int dailyMaxLimit = policyCache.getOrDefault("dailyMax", -1);

//        AtomicInteger hourlyCounter = hourlyAtomicCounter.computeIfAbsent(hourlyCacheKey, k ->
//                new AtomicInteger(reservationMapper.countByDateAndTime(resDate, resTime))
//        );

        // 💡 웜업이 보장되므로 무거운 computeIfAbsent(DB 조회)를 완전히 걷어내고 0으로 안전하게 정착합니다.
        AtomicInteger hourlyCounter = hourlyReservationCounter.computeIfAbsent(hourlyCacheKey, k -> new AtomicInteger(0));
        AtomicInteger dailyCounter = dailyReservationCounter.computeIfAbsent(dailyCacheKey, k -> new AtomicInteger(0));

        // 락 프리 무한루프 제어
        while (true) {
            int currentHourly = hourlyCounter.get();
            if (currentHourly >= hourlyMaxLimit) return false;

            if (hourlyCounter.compareAndSet(currentHourly, currentHourly + 1)) {

                // 당일 제한 원자적 체크
                if (dailyMaxLimit != -1) {
//                    AtomicInteger dailyCounter = dailyAtomicCounter.computeIfAbsent(dailyCacheKey, k ->
//                            new AtomicInteger(reservationMapper.countByDate(resDate))
//                    );
                    while (true) {
                        int currentDaily = dailyCounter.get();
                        if (currentDaily >= dailyMaxLimit) {
                            hourlyCounter.decrementAndGet(); // 시간대 원상복구
                            closeSlot(dailyCacheKey + ":DAILY");
                            return false;
                        }
//                      dailyCounter.incrementAndGet();

                        // 당일 카운트도 안전하게 CAS 성공할 때까지 돕니다.
                        if (dailyCounter.compareAndSet(currentDaily, currentDaily + 1)) {
                            break;
                        }
                    }
                }

                // 마감 판정 시 부모 통제
                if (hourlyCounter.get() >= hourlyMaxLimit) closeSlot(hourlyCacheKey);
//                if (dailyMaxLimit != -1 && dailyAtomicCounter.get(dailyCacheKey).get() >= dailyMaxLimit) {
                if (dailyMaxLimit != -1 && dailyCounter.get() >= dailyMaxLimit) {
                    closeSlot(dailyCacheKey + ":DAILY");
                }
                return true;
            }
        }
    }

    @Override
    protected void doInitCounters(String dailyKey, String hourlyKey, int count) {
        this.dailyReservationCounter.computeIfAbsent(dailyKey, k -> new java.util.concurrent.atomic.AtomicInteger(0))
                .addAndGet(count);
        this.hourlyReservationCounter.put(hourlyKey, new java.util.concurrent.atomic.AtomicInteger(count));
    }
}
