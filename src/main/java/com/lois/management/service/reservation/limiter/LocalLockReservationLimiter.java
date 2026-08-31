package com.lois.management.service.reservation.limiter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//@Component("localLockConcurrencyGuard")
@RequiredArgsConstructor
@Getter
public class LocalLockReservationLimiter extends AbstractReservationCacheLimiter {
//    private final ReservationCacheManager localLockCacheManager;
//    private final ReservationMapper reservationMapper;
//    private final ReservationPolicyMapper reservationPolicyMapper;
//
//    private final Map<String, Integer> policyCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyReservationCounter = new ConcurrentHashMap<>();
    private final Map<String, Integer> hourlyReservationCounter = new ConcurrentHashMap<>();

    private int dailyMaxLimit;
    private int hourlyMaxLimit;


    //    @PostConstruct
//    public void initCache() {
//        // 정책 확인 후 초기화 캐싱
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
        // 1. [데일리 차감] .merge()를 쓰면 자물쇠 없이도 안전하게 예약 한 건 차감
        getDailyReservationCounter().merge(dailyKey, -1, (oldVal, newVal) ->
                oldVal > 0 ? oldVal + newVal : 0
        );

        // 2. [시간대 차감] 동일하게 안전하게 예약 한 건 차감
        getHourlyReservationCounter().merge(hourlyKey, -1, (oldVal, newVal) ->
                oldVal > 0 ? oldVal + newVal : 0
        );
    }


    @Override
    public boolean doTryAcquireSlot(LocalDate resDate, LocalTime resTime) {
//        // 1. [부모 검문소] 플래그 체크 ➡️ true면 진행
//        if (!tryCheckFlag(resDate, resTime)) {
//            return false;
//        }

        String dailyCacheKey = resDate.toString();
        String hourlyCacheKey = resDate.toString() + ":" + resTime.getHour();

        // 1. 💡 [데일리 선제공격] 자물쇠 구역에 들어가기 전에, ConcurrentHashMap의 원자적 연산으로 1을 먼저 올려봅니다!
        if (dailyMaxLimit != -1) {
            // merge는 내부적으로 완벽한 원자성이 보장되어 동시성 버그가 절대 안 터집니다.
            int updatedDailyCount = dailyReservationCounter.merge(dailyCacheKey, 1, Integer::sum);

            // 만약 1을 올렸더니 한도를 초과했다? 그러면 즉시 취소하고 탈출시킵니다.
            if (updatedDailyCount > dailyMaxLimit) {
                dailyReservationCounter.merge(dailyCacheKey, -1, Integer::sum); // 줬던 1 다시 뺏기 (롤백)
                closeSlot(dailyCacheKey + ":DAILY");
                return false;
            }
        }

        // 2. [자물쇠] 시간대별 키로 동시성 방어벽 세우기
        synchronized (hourlyCacheKey.intern()) {

            // [시간대 제한 체크]
            int currentHourlyCount = hourlyReservationCounter.getOrDefault(hourlyCacheKey, 0);
            if (currentHourlyCount >= hourlyMaxLimit) {
                // 시간대 실패했으니 위에서 미리 올려둔 데일리 카운트는 정직하게 1 빼줘야 합니다.
                if (dailyMaxLimit != -1) {
                    dailyReservationCounter.merge(dailyCacheKey, -1, Integer::sum);
                }
                return false;
            }

            // [시간대 최종 업데이트]
            currentHourlyCount++;
            hourlyReservationCounter.put(hourlyCacheKey, currentHourlyCount);

            // 정확히 지금 만석이 되었다면 부모의 시간대 팻말 닫기
            if (currentHourlyCount >= hourlyMaxLimit) {
                closeSlot(hourlyCacheKey);
            }

            // 당일 수량도 최종 더블체크 후 만석이면 부모 팻말 닫기
            if (dailyMaxLimit != -1 && dailyReservationCounter.get(dailyCacheKey) >= dailyMaxLimit) {
                closeSlot(dailyCacheKey + ":DAILY");
            }

            return true;
        }
    }

    @Override
    protected void doInitCounters(String dailyKey, String hourlyKey, int count) {
        this.dailyReservationCounter.merge(dailyKey, count, Integer::sum);
        this.hourlyReservationCounter.put(hourlyKey, count);
    }
}
