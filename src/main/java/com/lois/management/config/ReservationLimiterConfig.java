package com.lois.management.config;

import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationLimiterConfig {

    @Bean
    @ConditionalOnProperty(
            name = "reservation.limiter.type",
            havingValue = "db-lock"
    )
    public ReservationLimiter dbLockConcurrencyGuard(ReservationMapper reservationMapper,  ReservationPolicyMapper reservationPolicyMapper) {
        return new DbLockReservationLimiter(reservationMapper, reservationPolicyMapper);
    }

    @Bean
    @ConditionalOnProperty(
            name = "reservation.limiter.type",
            havingValue = "local-lock",
            matchIfMissing = true
    )
    public ReservationLimiter localLockConcurrencyGuard() {
        return new LocalLockReservationLimiter();
    }

    @Bean
    @ConditionalOnProperty(
            name = "reservation.limiter.type",
            havingValue = "atomic"
    )
    public ReservationLimiter atomicConcurrencyGuard() {
        return new AtomicReservationLimiter();
    }


    @Bean
    @ConditionalOnProperty(
            name = "reservation.limiter.type",
            havingValue = "none"
    )
    public ReservationLimiter noOpConcurrencyGuard(ReservationMapper reservationMapper,  ReservationPolicyMapper reservationPolicyMapper) {
        return new NoOpReservationLimiter(reservationPolicyMapper, reservationMapper);
    }


}
