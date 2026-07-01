package com.lois.management.service.reservation.cache;

import com.lois.management.domain.ReservationPolicy;
import com.lois.management.dto.reservation.ReservationCountDto;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.AbstractReservationLimiter;
import com.lois.management.service.reservation.limiter.LocalLockReservationLimiter;
import com.lois.management.service.reservation.limiter.ReservationCacheLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        name = "reservation.limiter.type",
        havingValue = "local-lock",
        matchIfMissing = true
)
public class LocalLockCacheManager extends AbstractCacheManager{
//    private final ReservationPolicyMapper reservationPolicyMapper;
//    private final ReservationMapper reservationMapper;
//    private final ReservationCacheLimiter guard;
    private final Map<String, Integer> policyCache = new ConcurrentHashMap<>();
    public LocalLockCacheManager(
            ReservationPolicyMapper reservationPolicyMapper,
            ReservationMapper reservationMapper,
            ReservationCacheLimiter guard
    ) {
        super(reservationPolicyMapper, reservationMapper, guard);
    }


//    @PostConstruct
//    public void initCache() {
//        // 1. 정책 확인 후 초기화 캐싱
//        // db에서 정책 가져오기
//        ReservationPolicy policy = reservationPolicyMapper.selectLatestPolicy();
//        if (policy != null) {
//            // 하루당 정책 캐싱
//            policyCache.put("dailyMax", policy.getDailyMaxLimit());
//            // 시간당 정책 캐싱
//            policyCache.put("hourlyMax", policy.getHourlyMaxLimit());
//            // 락킹 리미터 변수에 정책 넣어주기(dailyMaxLimit, hourlyMaxLimit)
//            guard.updatePolicy(policy.getDailyMaxLimit(), policy.getHourlyMaxLimit());
//        }
//
//        // 2. 락킹 리미터 내 캐시맵 전부 지우기
//        guard.getDailyReservationCounter().clear();
//        guard.getHourlyReservationCounter().clear();
//
//        // 3. DB에서 오늘 이후 데이터 가져와서 락킹 리미터 내 맵 채우기 (Warm-up)
//        LocalDate startDate = LocalDate.now();
//        LocalDate endDate = startDate.plusDays(60);
//        // db에서 오늘부터 60일간 예약 데이터 가져오기
//        List<ReservationCountDto> reservationCounts = reservationMapper.countReservationsBetween(startDate, endDate);
//
//
//        System.out.println("🔥 [인메모리 캐시] 서버 기동 즉시 전체 예약 수량 동기화 완료!");
//    }

//    @Override
//    protected void afterbringdata(List<ReservationCountDto> reservationCounts) {
//        for (ReservationCountDto dto : reservationCounts) {
//            String dailyKey = dto.resDate().toString(); //"2026-06-10"
//            String hourlyKey = dto.resDate().toString() + ":" + dto.resTime().getHour(); //"2026-06-10:15"
//
//            // 락킹 리미터 내 dailyReservationCounter에 하루당 예약 데이터 넣어주기
////            {
////                "2026-06-10" : 2,
////                "2026-06-11" : 5
////            }
//            guard.getDailyReservationCounter().merge(dailyKey, dto.count(), Integer::sum);
//
//            // 락킹 리미터 내 hourlyReservationCounter에 시간당 예약 데이터 넣어주기
////            {
////                "2026-06-10:15" : 2,
////                "2026-06-10:16" : 0
////            }
//            guard.getHourlyReservationCounter().put(hourlyKey, dto.count());
//        }
//    }

//    @Override   // 예약 취소 시 락킹 리미터 캐시 데이터 수정
//    public void evictCache(LocalDate date, LocalTime time) {
//        String dailyCacheKey = date.toString(); //"2026-06-10"
//        String hourlyCacheKey = date.toString() + ":" + time.getHour(); //"2026-06-10:15"
//
//
//
//        // 3. 만석이어서 닫혔던 Flag(isClosedMap)들을 다시 활성화(True)로 개방
//        guard.openSlot(dailyCacheKey + ":DAILY");
//        guard.openSlot(hourlyCacheKey);
//    }
    @Override
    protected void forEvictCache(String dailyCacheKey, String hourlyCacheKey) {

        guard.decreaseCounters(dailyCacheKey, hourlyCacheKey);
    }

//    @Override  // 00시 00분에 이전 날짜에 대한 캐시 데이터 삭제
//    public void clearPastCache(LocalDate date, LocalTime time) {
//        LocalDate today = LocalDate.now(); // 2026-06-09
//
//        // 1. 데일리 카운터 청소 "2026-06-08"
//        guard.getDailyReservationCounter().keySet().removeIf(key -> {
//            try {
//                LocalDate cacheDate = LocalDate.parse(key);
//                return cacheDate.isBefore(today);
//            } catch (Exception e) {
//                return false;
//            }
//        });
//
//        // 2. 시간대 카운터 청소 "2026-06-08:14"
//        guard.getHourlyReservationCounter().keySet().removeIf(key -> {
//            try {
//                String datePart = key.split(":")[0];
//                LocalDate cacheDate = LocalDate.parse(datePart);
//                return cacheDate.isBefore(today);
//            } catch (Exception e) {
//                return false;
//            }
//        });
//
//        // 3. 부모 팻말(FlagMap)도 오늘 이전 날짜 삭제
//        guard.deletePastSlot(today);
//
//        System.out.println("🧹 [자정 대청소 완료] " + today + " 이전의 모든 과거 캐시 데이터가 정상적으로 완전히 삭제되었습니다.");
//    }
}
