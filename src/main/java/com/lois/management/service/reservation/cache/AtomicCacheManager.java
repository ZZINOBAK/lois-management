package com.lois.management.service.reservation.cache;

import com.lois.management.domain.ReservationPolicy;
import com.lois.management.dto.reservation.ReservationCountDto;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.AtomicReservationLimiter;
import com.lois.management.service.reservation.limiter.ReservationCacheLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(
        name = "reservation.limiter.type",
        havingValue = "atomic"
)
public class AtomicCacheManager extends AbstractCacheManager{
//    private final ReservationPolicyMapper reservationPolicyMapper;
//    private final ReservationMapper reservationMapper;
//    private final AtomicReservationLimiter guard;

    private final Map<String, Integer> policyCache = new ConcurrentHashMap<>();

    public AtomicCacheManager(
            ReservationPolicyMapper reservationPolicyMapper,
            ReservationMapper reservationMapper,
            ReservationCacheLimiter guard
    ) {
        super(reservationPolicyMapper, reservationMapper, guard);
    }

//    @PostConstruct
//    public void initCache() {
//        // 정책 확인 후 초기화 캐싱
//        ReservationPolicy policy = reservationPolicyMapper.selectLatestPolicy();
//        if (policy != null) {
//            policyCache.put("hourlyMax", policy.getHourlyMaxLimit());
//            policyCache.put("dailyMax", policy.getDailyMaxLimit());
//            guard.updatePolicy(policy.getDailyMaxLimit(), policy.getHourlyMaxLimit());
//        }
//
//        // 가드 내 캐시 전부 지우기
//        guard.getDailyAtomicCounter().clear();
//        guard.getHourlyAtomicCounter().clear();
//
//        // 3. DB에서 오늘 이후 데이터 가져와서 락킹 리미터 내 맵 채우기 (Warm-up)
//        LocalDate startDate = LocalDate.now();
//        LocalDate endDate = startDate.plusDays(60);
//
//        List<ReservationCountDto> reservationCounts = reservationMapper.countReservationsBetween(startDate, endDate);
//    }

//    @Override
//    protected void afterbringdata(List<ReservationCountDto> reservationCounts) {
//        for (ReservationCountDto dto : reservationCounts) {
//            String dailyKey = dto.resDate().toString();
//            String hourlyKey = dto.resDate().toString() + ":" + dto.resTime().getHour();
//
//            // 락킹 리미터 내 dailyReservationCounter에 하루당 예약 데이터 넣어주기 AtomicInteger용
//            guard.getDailyAtomicCounter()
//                    .computeIfAbsent(dailyKey, k -> new AtomicInteger(0))
//                    .addAndGet(dto.count());
//
//            // 가드의 당일 전체 캐시도 누적해서 채워주기 AtomicInteger용
//            guard.getHourlyAtomicCounter().put(hourlyKey, new AtomicInteger(dto.count()));
//        }
//    }

    @Override
    public void evictCache(LocalDate date, LocalTime time) {
        String dailyCacheKey = date.toString();
        String hourlyCacheKey = date.toString() + ":" + time.getHour();



        // 3. 만석이어서 닫혔던 Flag(isClosedMap)들을 다시 활성화(True)로 개방
        guard.openSlot(dailyCacheKey + ":DAILY");
        guard.openSlot(hourlyCacheKey);
    }

    @Override
    protected void forEvictCache(String dailyCacheKey, String hourlyCacheKey) {
        guard.decreaseCounters(dailyCacheKey, hourlyCacheKey);

    }

//    @Override
//    public void clearPastCache(LocalDate date, LocalTime time) {
//        LocalDate today = LocalDate.now(); // 2026-06-09
//
//        // 1. 데일리 카운터 청소 (키가 "2026-06-08" 형태)
//        guard.getDailyAtomicCounter().keySet().removeIf(key -> {
//            try {
//                LocalDate cacheDate = LocalDate.parse(key);
//                return cacheDate.isBefore(today);
//            } catch (Exception e) {
//                return false;
//            }
//        });
//
//        // 2. 시간대 카운터 청소 (키가 "2026-06-08:14" 이런 식이므로 앞의 날짜를 파싱해서 비교)
//        guard.getHourlyAtomicCounter().keySet().removeIf(key -> {
//            try {
//                String datePart = key.split(":")[0];
//                LocalDate cacheDate = LocalDate.parse(datePart);
//                return cacheDate.isBefore(today); // 오늘보다 이전 날짜면 맵에서 자동 삭제(true)
//            } catch (Exception e) {
//                return false;
//            }
//        });
//
//        // 3. 부모 팻말(FlagMap)도 오늘 이전 날짜 삭제
//        guard.deletePastSlot(today);
//        System.out.println("🧹 [자정 대청소 완료] " + today + " 이전의 모든 과거 캐시 데이터가 정상적으로 완전히 삭제되었습니다.");
//    }
}
