package com.lois.management.service.reservation.limiter;

import com.lois.management.domain.ReservationPolicy;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class NoOpReservationLimiter extends AbstractReservationCacheLimiter {
    private final ReservationPolicyMapper reservationPolicyMapper;
    private final ReservationMapper reservationMapper;

    /*
     * 부모 추상클래스의 요구사항을 충족하기 위한 Map이다.
     * 이 구현체에서는 실제 동시성 제어용으로 사용하지 않는다.
     */
    private final Map<String, Integer> dailyReservationCounter =
            new ConcurrentHashMap<>();

    private final Map<String, Integer> hourlyReservationCounter =
            new ConcurrentHashMap<>();

    public NoOpReservationLimiter(
            ReservationPolicyMapper reservationPolicyMapper,
            ReservationMapper reservationMapper
    ) {
        this.reservationPolicyMapper = reservationPolicyMapper;
        this.reservationMapper = reservationMapper;
    }

    @Override
    protected Map<String, ?> getDailyReservationCounter() {
        return dailyReservationCounter;
    }

    @Override
    protected Map<String, ?> getHourlyReservationCounter() {
        return hourlyReservationCounter;
    }

    @Override
    protected void doInitCounters(
            String dailyKey,
            String hourlyKey,
            int count
    ) {
        /*
         * No-Lock 비교군이므로 캐시 카운터를 초기화하거나
         * 동시성 제어에 사용하지 않는다.
         */
    }

    @Override
    protected boolean doTryAcquireSlot(
            LocalDate resDate,
            LocalTime resTime
    ) {
        /*
         * 정책 조회는 수행한다.
         * 단, SELECT ... FOR UPDATE는 사용하지 않는다.
         */
        ReservationPolicy policy =
                reservationPolicyMapper.selectLatestPolicy();

        /*
         * 현재 예약 수량도 DB에서 조회한다.
         * 조회와 INSERT 사이를 보호하는 락은 없다.
         */
        int dailyCount =
                reservationMapper.countByDate(resDate);

        int hourlyCount =
                reservationMapper.countByDateAndTime(
                        resDate,
                        resTime
                );

        boolean dailyAvailable =
                dailyCount < policy.getDailyMaxLimit();

        boolean hourlyAvailable =
                hourlyCount < policy.getHourlyMaxLimit();

        return dailyAvailable && hourlyAvailable;
    }

    @Override
    public void updatePolicy(
            int dailyMaxLimit,
            int hourlyMaxLimit
    ) {
        /*
         * 정책은 요청마다 DB에서 조회하므로
         * 메모리 정책 갱신은 하지 않는다.
         */
    }

    @Override
    public void clearCounters() {
        dailyReservationCounter.clear();
        hourlyReservationCounter.clear();
    }

    @Override
    public void decreaseCounters(
            String dailyKey,
            String hourlyKey
    ) {
        /*
         * 이 구현체는 메모리 카운터를 사용하지 않으므로
         * 감소 처리를 하지 않는다.
         */
    }
}