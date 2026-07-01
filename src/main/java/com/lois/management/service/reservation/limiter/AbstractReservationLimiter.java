package com.lois.management.service.reservation.limiter;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// 추상클래스 = 코드 재사용
@RequiredArgsConstructor
public abstract class AbstractReservationLimiter implements ReservationLimiter {

    private final Map<String, AtomicBoolean> isClosedMap = new ConcurrentHashMap<>();

    // 실시간으로 변경 가능하도록 volatile boolean으로 선언
    private volatile boolean enableFastFail = true;

    // 외부에서 켜고 끌 수 있는 리모컨 버튼(메서드)을 제공합니다.
    public final void setEnableFastFail(boolean enableFastFail) {
        this.enableFastFail = enableFastFail;
    }

    // 자식들이 만석을 감지하면 외부에서 이 리모컨을 눌러 팻말을 잠급니다.
    protected final void closeSlot(String cacheKey) {
        isClosedMap.computeIfAbsent(cacheKey, k -> new AtomicBoolean(false)).set(true);
        System.out.println("[" + Thread.currentThread().getName() + "] 🚨 마감 완료! 대상: " + cacheKey);
    }

    // 💡 [여기에 추가] 닫혔던 팻말을 다시 활짝 열어주는 오프너 메서드
    public final void openSlot(String cacheKey) {
        // 맵에 키가 존재할 때만 꺼내서 false(개방)로 바꿉니다.
        // 만약 초기화 시점이라 맵에 아예 없었다면, 새로 false 객체를 만들어 넣어줍니다.
        isClosedMap.computeIfAbsent(cacheKey, k -> new AtomicBoolean(false)).set(false);
        System.out.println("[" + Thread.currentThread().getName() + "] 🔓 개방 완료! 대상: " + cacheKey);
    }

    public final void deletePastSlot(LocalDate today) {
        this.isClosedMap.keySet().removeIf(key -> {
            try {
                // 시간대 키("2026-06-10:15")와 당일 키("2026-06-10")가 섞여 있을 수 있으므로
                // 콜론(:) 기준으로 쪼개서 무조건 앞쪽 날짜만 파싱합니다.
                String datePart = key.split(":")[0];
                LocalDate cacheDate = LocalDate.parse(datePart);

                return cacheDate.isBefore(today); // 오늘 이전 과거 날짜면 맵에서 자동 삭제
            } catch (Exception e) {
                return false; // 에러 나면 안전하게 패스
            }
        });
        System.out.println("🧹 [동시성 차단벽] 오늘 이전의 과거 마감 팻말(Flag) 청소 완료!");
    }

    protected final boolean tryCheckFlag(LocalDate date, LocalTime time) {
        String dailyCacheKey = date.toString() + ":DAILY";
        String hourlyCacheKey = date.toString() + ":" + time.getHour();

        // [거름망 1] 당일 전체 마감 팻말 확인 (0ms 초고속 패스)
        if (enableFastFail && isClosedMap.computeIfAbsent(dailyCacheKey, k -> new AtomicBoolean(false)).get()) {
            System.out.println("[" + Thread.currentThread().getName() + "] 🛡️ [당일 입구컷] DB 안 가고 튕겨냄");
            return false;
        }

        // [거름망 2] 시간대별 마감 팻말 확인
        if (enableFastFail && isClosedMap.computeIfAbsent(hourlyCacheKey, k -> new AtomicBoolean(false)).get()) {
            System.out.println("[" + Thread.currentThread().getName() + "] 🛡️ [시간대 입구컷] DB 안 가고 튕겨냄");
            return false;
        }

        return true;
    }








    @Override
    public final boolean tryAcquireSlot(LocalDate resDate, LocalTime resTime) {
        if (!tryCheckFlag(resDate, resTime)) {
            return false;
        }

        return doTryAcquireSlot(resDate, resTime);
    }

    protected abstract boolean doTryAcquireSlot(LocalDate resDate, LocalTime resTime);

}

