package com.lois.management.service;

import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationCreateReq;
import com.lois.management.dto.reservation.ReservationPageReq;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.ReservationLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)   // Mockito랑 JUnit5 연결
class ReservationsServiceTest {

    @Mock
    ReservationMapper reservationMapper;   // 가짜 Mapper
    @Mock
    ReservationPolicyMapper reservationPolicyMapper;
    @Mock
    ReservationLimiter reservationConcurrencyGuard;

    @InjectMocks
    ReservationService reservationService; // 위 가짜 mapper를 주입받은 진짜 서비스

    @Test
    void create_요청_DTO를_엔티티로_변환해서_insert까지_호출한다() {
        // given
        ReservationCreateReq req = new ReservationCreateReq(
                1L,                  // cakeId
                "2026-11-20",        // resDate
                "15:30",             // resTime
                "010-1234-5678",     // contact
                true                 // paid
        );

        when(reservationConcurrencyGuard.tryAcquireSlot(any(), any()))
                .thenReturn(true);
        when(reservationMapper.insert(any())).thenReturn(1);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);

        reservationService.create(req);

        verify(reservationMapper, times(1)).insert(captor.capture());

        Reservation saved = captor.getValue();
        assertThat(saved.getCakeId()).isEqualTo(1L);
        assertThat(saved.getResDate()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(saved.getResTime()).isEqualTo(LocalTime.parse("15:30"));
        assertThat(saved.getContact()).isEqualTo("010-1234-5678");
        assertThat(saved.getPaid()).isTrue();

        // 폰 뒷자리 컬럼 로직 체크
        assertThat(saved.getContactSuffix()).isEqualTo("5678");
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void create_웹과_API는_단일_트랜잭션으로_커밋한다(boolean api) {
        CountingTransactionManager manager = new CountingTransactionManager();
        ReservationService proxy = transactionalService(manager);
        when(reservationConcurrencyGuard.tryAcquireSlot(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return true;
        });
        when(reservationMapper.insert(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(manager.begins).isEqualTo(1);
            Reservation saved = invocation.getArgument(0);
            saved.setId(72L);
            return 1;
        });

        invokeCreate(proxy, api);

        verify(reservationMapper).insert(any());
        verify(reservationConcurrencyGuard).tryAcquireSlot(LocalDate.of(2026, 11, 20), LocalTime.of(15, 30));
        assertThat(manager.begins).isEqualTo(1);
        assertThat(manager.commits).isEqualTo(1);
        assertThat(manager.rollbacks).isZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void create_웹과_API는_INSERT_예외를_전달하고_롤백한다(boolean api) {
        CountingTransactionManager manager = new CountingTransactionManager();
        ReservationService proxy = transactionalService(manager);
        RuntimeException failure = new IllegalStateException("simulated INSERT failure");
        when(reservationConcurrencyGuard.tryAcquireSlot(any(), any())).thenReturn(true);
        when(reservationMapper.insert(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw failure;
        });

        assertThatThrownBy(() -> invokeCreate(proxy, api)).isSameAs(failure);

        verify(reservationMapper).insert(any());
        assertThat(manager.begins).isEqualTo(1);
        assertThat(manager.commits).isZero();
        assertThat(manager.rollbacks).isEqualTo(1);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private ReservationService transactionalService(CountingTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(reservationService);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (ReservationService) factory.getProxy();
    }

    private void invokeCreate(ReservationService proxy, boolean api) {
        if (api) {
            assertThat(proxy.create(new ReservationCreateReq(
                    1L, "2026-11-20", "15:30", "010-1234-5678", true))).isEqualTo(72L);
        } else {
            Reservation reservation = new Reservation();
            reservation.setCakeId(1L);
            reservation.setResDate(LocalDate.of(2026, 11, 20));
            reservation.setResTime(LocalTime.of(15, 30));
            reservation.setContact("010-1234-5678");
            reservation.setPaid(true);
            proxy.create(reservation);
            assertThat(reservation.getId()).isEqualTo(72L);
        }
    }

    private static class CountingTransactionManager extends AbstractPlatformTransactionManager {
        int begins;
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() { return new Object(); }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { begins++; }

        @Override
        protected void doCommit(DefaultTransactionStatus status) { commits++; }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
