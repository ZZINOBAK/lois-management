package com.lois.management.service.reservation.cache;

import java.time.LocalDate;
import java.time.LocalTime;

public interface ReservationCacheManager {


    /* 서버 기동/재연결 시 DB 데이터와 완벽 동기화 */
    void initCache();

    /* 예약 취소/정책 변경 시 캐시 삭제 */
    void evictCache(LocalDate date, LocalTime time);

    /* 현재 날짜 이전 예약건에 대한 캐시 삭제 */
    void clearPastCache(LocalDate date, LocalTime time);
}
