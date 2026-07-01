package com.lois.management.service.reservation.cache;

import com.lois.management.domain.ReservationPolicy;
import com.lois.management.dto.reservation.ReservationCountDto;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.ReservationCacheLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public abstract class AbstractCacheManager implements ReservationCacheManager {
    protected final ReservationPolicyMapper reservationPolicyMapper;
    protected final ReservationMapper reservationMapper;
    protected final ReservationCacheLimiter guard;

    protected final Map<String, Integer> policyCache = new ConcurrentHashMap<>();

    @PostConstruct
    @Override
    public void initCache() {
        // 1. 정책 확인 후 초기화 캐싱
        // db에서 정책 가져오기
        ReservationPolicy policy = reservationPolicyMapper.selectLatestPolicy();
        if (policy != null) {
            // 하루당 정책 캐싱
            policyCache.put("dailyMax", policy.getDailyMaxLimit());
            // 시간당 정책 캐싱
            policyCache.put("hourlyMax", policy.getHourlyMaxLimit());
            // 락킹 리미터 변수에 정책 넣어주기(dailyMaxLimit, hourlyMaxLimit)
            guard.updatePolicy(policy.getDailyMaxLimit(), policy.getHourlyMaxLimit());
        }

        // 2. 락킹 리미터 내 캐시맵 전부 지우기
//        guard.getDailyReservationCounter().clear();
//        guard.getHourlyReservationCounter().clear();
        guard.clearCounters();

        // 3. DB에서 오늘 이후 데이터 가져와서 락킹 리미터 내 맵 채우기 (Warm-up)
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(60);

        // db에서 오늘부터 60일간 예약 데이터 가져오기
        List<ReservationCountDto> reservationCounts = reservationMapper.countReservationsBetween(startDate, endDate);
//        afterbringdata(reservationCounts);
        for (ReservationCountDto dto : reservationCounts) {
            String dailyKey = dto.resDate().toString(); //"2026-06-10"
            String hourlyKey = dto.resDate().toString() + ":" + dto.resTime().getHour(); //"2026-06-10:15"
            guard.initCounters(dailyKey, hourlyKey, dto.count());
        }

        System.out.println("🔥 [인메모리 캐시] 서버 기동 즉시 전체 예약 수량 동기화 완료!");
    }
//    protected abstract void afterbringdata(List<ReservationCountDto> reservationCounts);


    @Override   // 예약 취소 시 락킹 리미터 캐시 데이터 수정
    public void evictCache(LocalDate date, LocalTime time) {
        String dailyCacheKey = date.toString(); //"2026-06-10"
        String hourlyCacheKey = date.toString() + ":" + time.getHour(); //"2026-06-10:15"

        forEvictCache(dailyCacheKey, hourlyCacheKey);

        guard.openSlot(dailyCacheKey + ":DAILY");
        guard.openSlot(hourlyCacheKey);
    }

    protected abstract void forEvictCache(String dailyCacheKey, String hourlyCacheKey);

    @Override
    public void clearPastCache(LocalDate date, LocalTime time) {
        LocalDate today = LocalDate.now();

//        guard.getDailyReservationCounter().keySet().removeIf(key -> {
//            try {
//                LocalDate cacheDate = LocalDate.parse(key);
//                return cacheDate.isBefore(today);
//            } catch (Exception e) {
//                return false;
//            }
//        });
//        guard.getHourlyReservationCounter().keySet().removeIf(key -> {
//            try {
//                String datePart = key.split(":")[0];
//                LocalDate cacheDate = LocalDate.parse(datePart);
//                return cacheDate.isBefore(today);
//            } catch (Exception e) {
//                return false;
//            }
//        });

        guard.deletePastCounters(today);
        guard.deletePastSlot(today);

        System.out.println("🧹 [" + this.getClass().getSimpleName() + " 자정 대청소 완료] " + today + " 이전의 모든 과거 캐시 데이터가 삭제되었습니다.");
    }


}
