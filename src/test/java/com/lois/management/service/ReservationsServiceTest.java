package com.lois.management.service;

import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationCreateReq;
import com.lois.management.dto.reservation.ReservationPageReq;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.CakeStockMapper;
import com.lois.management.mapper.CakeMovementMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
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

    @Mock
    CakeStockMapper cakeStockMapper;
    @Mock
    CakeMovementMapper cakeMovementMapper;

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

    @Test
    void pickupWithStockCommitsWritesInOrder() {
        CountingTransactionManager manager = new CountingTransactionManager();
        ReservationService proxy = transactionalService(manager);
        when(reservationMapper.findByIdForUpdate(7L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return pickupReservation("RESERVED");
        });
        when(cakeStockMapper.findQuantityForUpdate(3L, 2)).thenReturn(1);
        when(cakeStockMapper.decreaseStock(3L, 2)).thenReturn(1);
        when(reservationMapper.changeStatus(7L, "RESERVED", "PICKED_UP")).thenReturn(1);
        when(cakeMovementMapper.insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(true), eq(7L), anyString()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(UUID.fromString(invocation.getArgument(5, String.class)).toString())
                            .isEqualTo(invocation.getArgument(5, String.class));
                    return 1;
                });

        assertThat(proxy.pickup(7L)).isTrue();

        var order = inOrder(reservationMapper, cakeStockMapper, cakeMovementMapper);
        order.verify(reservationMapper).findByIdForUpdate(7L);
        order.verify(cakeStockMapper).findQuantityForUpdate(3L, 2);
        order.verify(cakeStockMapper).decreaseStock(3L, 2);
        order.verify(reservationMapper).changeStatus(7L, "RESERVED", "PICKED_UP");
        order.verify(cakeMovementMapper).insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(true), eq(7L), anyString());
        assertThat(manager.begins).isEqualTo(1);
        assertThat(manager.commits).isEqualTo(1);
        assertThat(manager.rollbacks).isZero();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(ints = {0})
    void pickupWithoutStockCommitsWithoutDecrement(Integer quantity) {
        CountingTransactionManager manager = new CountingTransactionManager();
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation("RESERVED"));
        when(cakeStockMapper.findQuantityForUpdate(3L, 2)).thenReturn(quantity);
        when(reservationMapper.changeStatus(7L, "RESERVED", "PICKED_UP")).thenReturn(1);
        when(cakeMovementMapper.insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(false), eq(7L), anyString())).thenReturn(1);

        assertThat(transactionalService(manager).pickup(7L)).isTrue();

        verify(cakeStockMapper, never()).decreaseStock(anyLong(), anyInt());
        var order = inOrder(reservationMapper, cakeMovementMapper);
        order.verify(reservationMapper).changeStatus(7L, "RESERVED", "PICKED_UP");
        order.verify(cakeMovementMapper).insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(false), eq(7L), anyString());
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void alreadyPickedUpDoesNotChangeAnything() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation("PICKED_UP"));
        assertThat(transactionalService(new CountingTransactionManager()).pickup(7L)).isFalse();
        verify(reservationMapper, never()).changeStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(cakeStockMapper, cakeMovementMapper);
    }

    @Test
    void repeatedPickupWritesOnlyOnce() {
        var manager = new CountingTransactionManager();
        var proxy = transactionalService(manager);
        when(reservationMapper.findByIdForUpdate(7L))
                .thenReturn(pickupReservation("RESERVED"), pickupReservation("PICKED_UP"));
        when(cakeStockMapper.findQuantityForUpdate(3L, 2)).thenReturn(1);
        when(cakeStockMapper.decreaseStock(3L, 2)).thenReturn(1);
        when(reservationMapper.changeStatus(7L, "RESERVED", "PICKED_UP")).thenReturn(1);
        when(cakeMovementMapper.insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(true), eq(7L), anyString())).thenReturn(1);
        assertThat(proxy.pickup(7L)).isTrue();
        assertThat(proxy.pickup(7L)).isFalse();
        verify(cakeStockMapper, times(1)).decreaseStock(3L, 2);
        verify(cakeMovementMapper, times(1)).insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(true), eq(7L), anyString());
    }

    @Test
    void missingPickupReservationIsNotFound() {
        var manager = new CountingTransactionManager();
        assertThatThrownBy(() -> transactionalService(manager).pickup(7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(cakeStockMapper, cakeMovementMapper);
        assertPickupRolledBack(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WAITING", "CANCELLED"})
    void nonReservedPickupIsRejected(String status) {
        var manager = new CountingTransactionManager();
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation(status));
        assertThatThrownBy(() -> transactionalService(manager).pickup(7L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(reservationMapper, never()).changeStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(cakeStockMapper, cakeMovementMapper);
        assertPickupRolledBack(manager);
    }

    @Test
    void duplicatePickupStockAbortsBeforeWrites() {
        var manager = new CountingTransactionManager();
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation("RESERVED"));
        var failure = new org.apache.ibatis.exceptions.TooManyResultsException("duplicate");
        when(cakeStockMapper.findQuantityForUpdate(3L, 2)).thenThrow(failure);
        assertThatThrownBy(() -> transactionalService(manager).pickup(7L)).isSameAs(failure);
        verify(reservationMapper, never()).changeStatus(anyLong(), anyString(), anyString());
        verify(cakeStockMapper, never()).decreaseStock(anyLong(), anyInt());
        verifyNoInteractions(cakeMovementMapper);
        assertPickupRolledBack(manager);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void pickupDecreaseCountFailureRollsBack(int count) {
        var manager = new CountingTransactionManager();
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation("RESERVED"));
        when(cakeStockMapper.findQuantityForUpdate(3L, 2)).thenReturn(1);
        when(cakeStockMapper.decreaseStock(3L, 2)).thenReturn(count);
        assertThatThrownBy(() -> transactionalService(manager).pickup(7L)).isInstanceOf(IllegalStateException.class);
        verify(reservationMapper, never()).changeStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(cakeMovementMapper);
        assertPickupRolledBack(manager);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void pickupStatusCountFailureRollsBack(int count) {
        var manager = new CountingTransactionManager();
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation("RESERVED"));
        when(cakeStockMapper.findQuantityForUpdate(3L, 2)).thenReturn(1);
        when(cakeStockMapper.decreaseStock(3L, 2)).thenReturn(1);
        when(reservationMapper.changeStatus(7L, "RESERVED", "PICKED_UP")).thenReturn(count);
        assertThatThrownBy(() -> transactionalService(manager).pickup(7L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cakeMovementMapper);
        assertPickupRolledBack(manager);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void pickupMovementCountFailureRollsBack(int count) {
        var manager = new CountingTransactionManager();
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation("RESERVED"));
        when(reservationMapper.changeStatus(7L, "RESERVED", "PICKED_UP")).thenReturn(1);
        when(cakeMovementMapper.insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(false), eq(7L), anyString())).thenReturn(count);
        assertThatThrownBy(() -> transactionalService(manager).pickup(7L)).isInstanceOf(IllegalStateException.class);
        assertPickupRolledBack(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"decrease", "status", "movement"})
    void pickupSqlFailuresRollBack(String stage) {
        var manager = new CountingTransactionManager();
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pickupReservation("RESERVED"));
        when(cakeStockMapper.findQuantityForUpdate(3L, 2)).thenReturn(1);
        var failure = new IllegalStateException("simulated " + stage);
        if (stage.equals("decrease")) {
            when(cakeStockMapper.decreaseStock(3L, 2)).thenThrow(failure);
        } else {
            when(cakeStockMapper.decreaseStock(3L, 2)).thenReturn(1);
            if (stage.equals("status")) {
                when(reservationMapper.changeStatus(7L, "RESERVED", "PICKED_UP")).thenThrow(failure);
            } else {
                when(reservationMapper.changeStatus(7L, "RESERVED", "PICKED_UP")).thenReturn(1);
                when(cakeMovementMapper.insertPickupMovement(eq(3L), eq(2), eq("PICKED_UP"), eq(true), eq(7L), anyString()))
                        .thenThrow(failure);
            }
        }
        assertThatThrownBy(() -> transactionalService(manager).pickup(7L)).isSameAs(failure);
        assertPickupRolledBack(manager);
    }

    @Test
    void pickupReadSqlLocksReservation() throws Exception {
        var sql = pickupSql("ReservationMapper", "findByIdForUpdate", Map.of("reservationId", 7L));
        assertThat(sql.getSql()).contains("SELECT id, cake_id, cake_size, status", "WHERE id = ?", "FOR UPDATE");
        assertThat(sql.getParameterMappings()).extracting("property").containsExactly("reservationId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PICKED_UP", "CANCELLED"})
    void changeStatusSqlOnlySetsPickupTimeForPickedUp(String newStatus) throws Exception {
        var sql = pickupSql("ReservationMapper", "changeStatus",
                Map.of("reservationId", 7L, "expectedStatus", "RESERVED", "newStatus", newStatus));
        assertThat(sql.getSql()).contains("status = ?", "updated_at = NOW()", "WHERE id = ?", "AND status = ?");
        if ("PICKED_UP".equals(newStatus)) {
            assertThat(sql.getSql()).contains("picked_up_at = NOW()");
        } else {
            assertThat(sql.getSql()).doesNotContain("picked_up_at");
        }
        assertThat(sql.getParameterMappings()).extracting("property")
                .containsExactly("newStatus", "reservationId", "expectedStatus");
    }

    @Test
    void pickupMovementSqlBindsReservationAndStockFlag() throws Exception {
        var sql = pickupSql("CakeMovementMapper", "insertPickupMovement",
                Map.of("cakeId", 3L, "cakeSize", 2, "moveType", "PICKED_UP",
                        "stockChanged", false, "reservationId", 7L, "requestId", "test"));
        assertThat(sql.getSql()).contains("(cake_id, cake_size, move_type, stock_changed, reservation_id, request_id)")
                .doesNotContain("delta", "biz_date");
        assertThat(sql.getParameterMappings()).extracting("property")
                .containsExactly("cakeId", "cakeSize", "moveType", "stockChanged", "reservationId", "requestId");
    }

    private Reservation pickupReservation(String status) {
        Reservation reservation = new Reservation();
        reservation.setId(7L);
        reservation.setCakeId(3L);
        reservation.setCakeSize(2);
        reservation.setStatus(status);
        return reservation;
    }

    private void assertPickupRolledBack(CountingTransactionManager manager) {
        assertThat(manager.commits).isZero();
        assertThat(manager.rollbacks).isEqualTo(1);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private org.apache.ibatis.mapping.BoundSql pickupSql(String mapper, String statement, Object parameters) throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lois.management.domain");
        try (InputStream input = getClass().getResourceAsStream("/mapper/" + mapper + ".xml")) {
            new XMLMapperBuilder(input, configuration, mapper + ".xml", configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement("com.lois.management.mapper." + mapper + "." + statement)
                .getBoundSql(parameters);
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
