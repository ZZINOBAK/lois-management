package com.lois.management.service.reservation.limiter;

import com.lois.management.domain.ReservationPolicy;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

//@Component("dbLockConcurrencyGuard")
@RequiredArgsConstructor
public class DbLockReservationLimiter extends AbstractReservationLimiter {
    private final ReservationMapper reservationMapper;
    private final ReservationPolicyMapper reservationPolicyMapper;
    @Override
    public boolean doTryAcquireSlot(LocalDate resDate, LocalTime resTime) {
        // 1. [부모 검문소] 팻말 확인 ➡️ 이미 문 닫혔으면 DB 안 찌르고 즉시 튕김 (DB 보호)
//        if (!tryCheckFlag(resDate, resTime)) {
//            return false;
//        }

        String dailyCacheKey = resDate.toString();
        String hourlyCacheKey = resDate.toString() + ":" + resTime.getHour();

        // 2. [DB 비관적 락] 정책 테이블에 락을 걸어 트랜잭션 줄 세우기 시작
        ReservationPolicy policy = reservationPolicyMapper.selectLatestPolicyWithLock();

        if (policy != null) {
            System.out.println("정책 조회 완료");
            int dailyMaxLimit = policy.getDailyMaxLimit();
            int hourlyMaxLimit = policy.getHourlyMaxLimit();

            // -------------------------------------------------------------
            // [당일 제한 체크]
            // -------------------------------------------------------------
            if (dailyMaxLimit != -1) {
                int currentDailyCount = reservationMapper.countByDate(resDate);
                System.out.println("날짜 예약 수량 확인");
                // 이미 오늘 자리가 꽉 차 있다면?
                if (currentDailyCount >= dailyMaxLimit) {
                    closeSlot(dailyCacheKey + ":DAILY"); // 부모한테 "오늘 마감이야!" 팻말 걸기
                    return false;
                }

                // 내가 통과해서 오늘 최종 수량이 만석이 될 예정이라면? 미리 팻말 걸기
                if (currentDailyCount + 1 >= dailyMaxLimit) {
                    closeSlot(dailyCacheKey + ":DAILY");
                }
            }

            // -------------------------------------------------------------
            // [시간대별 제한 체크]
            // -------------------------------------------------------------
            int currentHourlyCount = reservationMapper.countByDateAndTime(resDate, resTime);
            System.out.println("날짜+시간 예약 수량 확인");

            // 이미 이 시간대가 꽉 차 있다면?
            if (currentHourlyCount >= hourlyMaxLimit) {
                closeSlot(hourlyCacheKey); // 부모한테 "이 시간 마감이야!" 팻말 걸기
                return false;
            }

            // 내가 통과해서 이 시간대가 최종 만석이 될 예정이라면? 미리 팻말 걸기
            if (currentHourlyCount + 1 >= hourlyMaxLimit) {
                closeSlot(hourlyCacheKey);
            }
        }

        return true; // 모든 검증 완벽 통과, 비관적 락 보호 아래 안전하게 예약 진행 🚀
    }
}

