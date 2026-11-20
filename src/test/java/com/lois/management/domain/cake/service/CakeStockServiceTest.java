package com.lois.management.domain.cake.service;

import com.lois.management.domain.cake.CakeMovement;
import com.lois.management.domain.cake.mapper.CakeMovementMapper;
import com.lois.management.domain.cake.mapper.CakeStockMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.lois.management.global.sse.DataChangedEvent;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CakeStockServiceTest {
    private final CakeStockMapper stocks = mock(CakeStockMapper.class);
    private final CakeMovementMapper movements = mock(CakeMovementMapper.class);
    private final CountingTransactionManager manager = new CountingTransactionManager();
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CakeStockService service = transactionalService();

    @Test
    void productionCommitsBothWritesInOrderWithGeneratedValues() {
        when(stocks.increaseStock(1L, 2)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1;
        });
        when(movements.insertStockMovement(eq(1L), eq(2), eq("PRODUCED"), eq(true), anyString()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(UUID.fromString(invocation.getArgument(4, String.class)).toString())
                            .isEqualTo(invocation.getArgument(4, String.class));
                    return 1;
                });
        service.produce(1L, 2);
        var order = inOrder(stocks, movements);
        order.verify(stocks).increaseStock(1L, 2);
        order.verify(movements).insertStockMovement(eq(1L), eq(2), eq("PRODUCED"), eq(true), anyString());
        assertThat(manager.commits).isEqualTo(1);
        assertThat(manager.begins).isEqualTo(1);
        assertThat(manager.rollbacks).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void invalidStockRowCountRollsBackAndSkipsMovement(int count) {
        when(stocks.increaseStock(1L, 2)).thenReturn(count);
        assertThatThrownBy(() -> service.produce(1L, 2)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @Test
    void stockFailureRollsBackAndSkipsMovement() {
        when(stocks.increaseStock(1L, 2)).thenThrow(new IllegalStateException("stock failure"));
        assertThatThrownBy(() -> service.produce(1L, 2)).hasMessage("stock failure");
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @Test
    void movementFailureRollsBack() {
        when(stocks.increaseStock(1L, 2)).thenReturn(1);
        when(movements.insertStockMovement(anyLong(), anyInt(), anyString(), anyBoolean(), anyString()))
                .thenThrow(new IllegalStateException("movement failure"));
        assertThatThrownBy(() -> service.produce(1L, 2)).hasMessage("movement failure");
        assertRolledBack();
    }

    @Test
    void missingMovementInsertRollsBack() {
        when(stocks.increaseStock(1L, 2)).thenReturn(1);
        assertThatThrownBy(() -> service.produce(1L, 2)).isInstanceOf(IllegalStateException.class);
        assertRolledBack();
    }

    @Test
    void stockSqlIncrementsOnlyMatchingRows() throws Exception {
        String sql = sql("CakeStockMapper", "increaseStock", Map.of("cakeId", 1L, "cakeSize", 2));
        assertThat(sql).contains("quantity = quantity + 1", "updated_at = NOW()", "WHERE cake_id = ?", "AND cake_size = ?");
    }

    @Test
    void movementSqlLeavesLegacyColumnsToDefaults() throws Exception {
        String sql = sql("CakeMovementMapper", "insertStockMovement",
                Map.of("cakeId", 1L, "cakeSize", 2, "moveType", "PRODUCED", "stockChanged", true, "requestId", "test"));
        assertThat(sql).contains("(cake_id, cake_size, move_type, stock_changed, request_id)")
                .doesNotContain("reservation_id", "delta", "biz_date");
    }

    @Test
    void cancellationCommitsBothWritesAfterLockedRead() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1;
        });
        when(stocks.decreaseStock(1L, 2)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1;
        });
        when(movements.insertStockMovement(eq(1L), eq(2), eq("UNDO_PRODUCED"), eq(true), anyString()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(UUID.fromString(invocation.getArgument(4, String.class)).toString())
                            .isEqualTo(invocation.getArgument(4, String.class));
                    return 1;
                });

        assertThat(service.cancelProduction(1L, 2)).isTrue();

        var order = inOrder(stocks, movements);
        order.verify(stocks).findQuantityForUpdate(1L, 2);
        order.verify(stocks).decreaseStock(1L, 2);
        order.verify(movements).insertStockMovement(eq(1L), eq(2), eq("UNDO_PRODUCED"), eq(true), anyString());
        assertThat(manager.begins).isEqualTo(1);
        assertThat(manager.commits).isEqualTo(1);
        assertThat(manager.rollbacks).isZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void cancellationWithZeroStockDoesNotWrite() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(0);
        assertThat(service.cancelProduction(1L, 2)).isFalse();
        verify(stocks, never()).decreaseStock(anyLong(), anyInt());
        verifyNoInteractions(movements);
    }

    @Test
    void cancellationWithoutStockRowDoesNotWrite() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(null);
        assertThat(service.cancelProduction(1L, 2)).isFalse();
        verify(stocks, never()).decreaseStock(anyLong(), anyInt());
        verifyNoInteractions(movements);
    }

    @Test
    void duplicateStockRowsAbortCancellation() {
        var failure = new org.apache.ibatis.exceptions.TooManyResultsException("duplicate stocks");
        when(stocks.findQuantityForUpdate(1L, 2)).thenThrow(failure);
        assertThatThrownBy(() -> service.cancelProduction(1L, 2)).isSameAs(failure);
        verify(stocks, never()).decreaseStock(anyLong(), anyInt());
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void cancellationWithInvalidDecreaseCountRollsBack(int count) {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(3);
        when(stocks.decreaseStock(1L, 2)).thenReturn(count);
        assertThatThrownBy(() -> service.cancelProduction(1L, 2)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @Test
    void cancellationDecreaseFailureRollsBack() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(3);
        var failure = new IllegalStateException("decrease failure");
        when(stocks.decreaseStock(1L, 2)).thenThrow(failure);
        assertThatThrownBy(() -> service.cancelProduction(1L, 2)).isSameAs(failure);
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @Test
    void cancellationMovementFailureRollsBack() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(3);
        when(stocks.decreaseStock(1L, 2)).thenReturn(1);
        var failure = new IllegalStateException("movement failure");
        when(movements.insertStockMovement(eq(1L), eq(2), eq("UNDO_PRODUCED"), eq(true), anyString()))
                .thenThrow(failure);
        assertThatThrownBy(() -> service.cancelProduction(1L, 2)).isSameAs(failure);
        assertRolledBack();
    }

    @Test
    void cancellationMissingMovementRollsBack() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(3);
        when(stocks.decreaseStock(1L, 2)).thenReturn(1);
        assertThatThrownBy(() -> service.cancelProduction(1L, 2)).isInstanceOf(IllegalStateException.class);
        assertRolledBack();
    }

    @Test
    void cancellationReadSqlLocksMatchingStockAndBindsKeys() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/cake/CakeStockMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "CakeStockMapper.xml", configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(CakeStockMapper.class.getName() + ".findQuantityForUpdate");
        assertThat(statement.getResultMaps().getFirst().getType()).isEqualTo(Integer.class);
        var boundSql = statement.getBoundSql(Map.of("cakeId", 1L, "cakeSize", 2));
        assertThat(boundSql.getSql()).contains("SELECT quantity", "WHERE cake_id = ?", "AND cake_size = ?", "FOR UPDATE");
        assertThat(boundSql.getParameterMappings()).extracting("property").containsExactly("cakeId", "cakeSize");
    }

    @Test
    void cancellationDecreaseSqlPreventsNegativeStock() throws Exception {
        String statement = sql("CakeStockMapper", "decreaseStock", Map.of("cakeId", 1L, "cakeSize", 2));
        assertThat(statement).contains("quantity = quantity - 1", "updated_at = NOW()",
                "WHERE cake_id = ?", "AND cake_size = ?", "AND quantity > 0");
    }

    @Test
    void onSiteSaleWithStockCommitsDecreaseAndMovementInOrder() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1;
        });
        when(stocks.decreaseStock(1L, 2)).thenReturn(1);
        when(movements.insertStockMovement(eq(1L), eq(2), eq("ON_SITE_SOLD"), eq(true), anyString()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(UUID.fromString(invocation.getArgument(4, String.class)).toString())
                            .isEqualTo(invocation.getArgument(4, String.class));
                    return 1;
                });

        service.sellOnSite(1L, 2);

        var order = inOrder(stocks, movements);
        order.verify(stocks).findQuantityForUpdate(1L, 2);
        order.verify(stocks).decreaseStock(1L, 2);
        order.verify(movements).insertStockMovement(eq(1L), eq(2), eq("ON_SITE_SOLD"), eq(true), anyString());
        assertThat(manager.begins).isEqualTo(1);
        assertThat(manager.commits).isEqualTo(1);
        assertThat(manager.rollbacks).isZero();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(ints = {0})
    void onSiteSaleWithoutStockRecordsMovementWithoutDecrease(Integer quantity) {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(quantity);
        when(movements.insertStockMovement(eq(1L), eq(2), eq("ON_SITE_SOLD"), eq(false), anyString())).thenReturn(1);

        service.sellOnSite(1L, 2);

        verify(stocks, never()).decreaseStock(anyLong(), anyInt());
        verify(movements).insertStockMovement(eq(1L), eq(2), eq("ON_SITE_SOLD"), eq(false), anyString());
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void onSiteSaleDuplicateStocksAbortBeforeWrites() {
        var failure = new org.apache.ibatis.exceptions.TooManyResultsException("duplicate stocks");
        when(stocks.findQuantityForUpdate(1L, 2)).thenThrow(failure);
        assertThatThrownBy(() -> service.sellOnSite(1L, 2)).isSameAs(failure);
        verify(stocks, never()).decreaseStock(anyLong(), anyInt());
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void onSiteSaleInvalidDecreaseCountRollsBack(int count) {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(1);
        when(stocks.decreaseStock(1L, 2)).thenReturn(count);
        assertThatThrownBy(() -> service.sellOnSite(1L, 2)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @Test
    void onSiteSaleDecreaseFailureRollsBack() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(1);
        var failure = new IllegalStateException("decrease failure");
        when(stocks.decreaseStock(1L, 2)).thenThrow(failure);
        assertThatThrownBy(() -> service.sellOnSite(1L, 2)).isSameAs(failure);
        verifyNoInteractions(movements);
        assertRolledBack();
    }

    @Test
    void onSiteSaleMovementFailureRollsBackDecrease() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(1);
        when(stocks.decreaseStock(1L, 2)).thenReturn(1);
        var failure = new IllegalStateException("sale movement failure");
        when(movements.insertStockMovement(eq(1L), eq(2), eq("ON_SITE_SOLD"), eq(true), anyString())).thenThrow(failure);
        assertThatThrownBy(() -> service.sellOnSite(1L, 2)).isSameAs(failure);
        assertRolledBack();
    }

    @Test
    void onSiteSaleMovementFailureWithoutStockRollsBack() {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(0);
        var failure = new IllegalStateException("sale movement failure");
        when(movements.insertStockMovement(eq(1L), eq(2), eq("ON_SITE_SOLD"), eq(false), anyString())).thenThrow(failure);
        assertThatThrownBy(() -> service.sellOnSite(1L, 2)).isSameAs(failure);
        verify(stocks, never()).decreaseStock(anyLong(), anyInt());
        assertRolledBack();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void onSiteSaleInvalidMovementCountRollsBack(int count) {
        when(stocks.findQuantityForUpdate(1L, 2)).thenReturn(1);
        when(stocks.decreaseStock(1L, 2)).thenReturn(1);
        when(movements.insertStockMovement(eq(1L), eq(2), eq("ON_SITE_SOLD"), eq(true), anyString())).thenReturn(count);
        assertThatThrownBy(() -> service.sellOnSite(1L, 2)).isInstanceOf(IllegalStateException.class);
        assertRolledBack();
    }

    @Test
    void onSiteSaleSqlBindsSharedMovementParameters() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lois.management.domain");
        try (InputStream input = getClass().getResourceAsStream("/mapper/cake/CakeMovementMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "CakeMovementMapper.xml", configuration.getSqlFragments()).parse();
        }
        var boundSql = configuration.getMappedStatement(CakeMovementMapper.class.getName() + ".insertStockMovement")
                .getBoundSql(Map.of("cakeId", 1L, "cakeSize", 2, "moveType", "ON_SITE_SOLD",
                        "stockChanged", false, "requestId", "test"));
        assertThat(boundSql.getParameterMappings()).extracting("property")
                .containsExactly("cakeId", "cakeSize", "moveType", "stockChanged", "requestId");
        assertThat(boundSql.getSql()).doesNotContain("reservation_id", "delta", "biz_date");
    }

    // ===== 일일 재고 초기화 =====
    private static final LocalDate RESET_DAY = LocalDate.of(2026, 11, 21);

    private static CakeMovement stock(long cakeId, int cakeSize, Integer quantity) {
        CakeMovement row = new CakeMovement();
        row.setCakeId(cakeId);
        row.setCakeSize(cakeSize);
        row.setStock(quantity);
        return row;
    }

    @Test
    void dailyResetLocksStocksRecordsEveryRowThenZeroesAllInOneTransaction() {
        when(stocks.findAllStocksForUpdate()).thenReturn(List.of(stock(1L, 1, 0), stock(1L, 2, 3)));
        when(movements.existsDailyReset(RESET_DAY)).thenReturn(false);
        when(movements.insertDailyResetMovement(any())).thenReturn(1);
        when(stocks.resetAllStocks()).thenReturn(1);

        assertThat(service.resetDailyStocks(RESET_DAY, "TEST")).isTrue();

        var order = inOrder(stocks, movements);
        order.verify(stocks).findAllStocksForUpdate();
        order.verify(movements).existsDailyReset(RESET_DAY);
        var captor = ArgumentCaptor.forClass(CakeMovement.class);
        order.verify(movements, times(2)).insertDailyResetMovement(captor.capture());
        order.verify(stocks).resetAllStocks();
        var zero = captor.getAllValues().get(0);
        var three = captor.getAllValues().get(1);
        assertThat(zero.getDelta()).isZero();
        assertThat(zero.getStockChanged()).isFalse();
        assertThat(three.getBizDate()).isEqualTo(RESET_DAY);
        assertThat(three.getMoveType()).isEqualTo("DAILY_RESET");
        assertThat(three.getDelta()).isEqualTo(-3);
        assertThat(three.getStockChanged()).isTrue();
        assertThat(three.getRequestId()).isEqualTo("RESET-20261121-1-2").hasSizeLessThanOrEqualTo(36);
        assertThat(three.getMemo()).contains("3");
        assertThat(manager.begins).isEqualTo(1);
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void dailyResetPublishesChangeOnlyAfterCommit() {
        when(stocks.findAllStocksForUpdate()).thenReturn(List.of(stock(1L, 2, 1)));
        when(movements.insertDailyResetMovement(any())).thenReturn(1);
        doAnswer(invocation -> {
            assertThat(manager.commits).isEqualTo(1);
            return null;
        }).when(events).publishEvent(any(DataChangedEvent.class));

        service.resetDailyStocks(RESET_DAY, "TEST");

        verify(events).publishEvent(any(DataChangedEvent.class));
    }

    @Test
    void dailyResetSkipsWhenAlreadyResetForThatDay() {
        when(stocks.findAllStocksForUpdate()).thenReturn(List.of(stock(1L, 2, 4)));
        when(movements.existsDailyReset(RESET_DAY)).thenReturn(true);

        assertThat(service.resetDailyStocks(RESET_DAY, "TEST")).isFalse();

        verify(movements, never()).insertDailyResetMovement(any());
        verify(stocks, never()).resetAllStocks();
        verifyNoInteractions(events);
    }

    @Test
    void dailyResetMovementFailureRollsBackWithoutZeroingStock() {
        when(stocks.findAllStocksForUpdate()).thenReturn(List.of(stock(1L, 2, 2)));
        when(movements.insertDailyResetMovement(any())).thenThrow(new IllegalStateException("duplicate request"));

        assertThatThrownBy(() -> service.resetDailyStocks(RESET_DAY, "TEST")).isInstanceOf(IllegalStateException.class);

        verify(stocks, never()).resetAllStocks();
        verifyNoInteractions(events);
        assertRolledBack();
    }

    @Test
    void startupResetSwallowsFailureSoApplicationKeepsRunning() {
        when(stocks.findAllStocksForUpdate()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(service::resetStocksOnStartup).doesNotThrowAnyException();
        assertThatCode(service::resetStocksAtMidnight).doesNotThrowAnyException();
    }

    @Test
    void disabledResetNeverTouchesStocks() {
        CakeStockService disabled = new CakeStockService(stocks, movements, manager, events, false);

        disabled.resetStocksOnStartup();
        disabled.resetStocksAtMidnight();

        verifyNoInteractions(stocks, movements, events);
    }

    @Test
    void midnightScheduleRunsAtZeroOClockKoreaTime() throws Exception {
        Scheduled scheduled = CakeStockService.class.getMethod("resetStocksAtMidnight").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void dailyResetSqlLocksAllStocksAndZeroesOnlyNonZeroRows() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lois.management.domain");
        try (InputStream input = getClass().getResourceAsStream("/mapper/cake/CakeStockMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "CakeStockMapper.xml", configuration.getSqlFragments()).parse();
        }
        String select = configuration.getMappedStatement(CakeStockMapper.class.getName() + ".findAllStocksForUpdate")
                .getBoundSql(null).getSql();
        assertThat(select).contains("FROM cake_stocks", "quantity  AS stock", "FOR UPDATE").doesNotContain("WHERE");
        String reset = configuration.getMappedStatement(CakeStockMapper.class.getName() + ".resetAllStocks")
                .getBoundSql(null).getSql();
        assertThat(reset).contains("SET quantity = 0", "WHERE quantity <> 0").doesNotContain("cake_id =");
    }

    @Test
    void dailyResetMovementSqlStoresAppDateAndChecksByDate() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lois.management.domain");
        try (InputStream input = getClass().getResourceAsStream("/mapper/cake/CakeMovementMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "CakeMovementMapper.xml", configuration.getSqlFragments()).parse();
        }
        var insert = configuration.getMappedStatement(CakeMovementMapper.class.getName() + ".insertDailyResetMovement")
                .getBoundSql(new CakeMovement());
        assertThat(insert.getParameterMappings()).extracting("property").containsExactly(
                "bizDate", "cakeId", "cakeSize", "delta", "moveType", "stockChanged", "requestId", "memo");
        String exists = configuration.getMappedStatement(CakeMovementMapper.class.getName() + ".existsDailyReset")
                .getBoundSql(Map.of("bizDate", RESET_DAY)).getSql();
        assertThat(exists).contains("move_type = 'DAILY_RESET'", "biz_date = ?");
    }

    private String sql(String mapper, String statement, Object parameter) throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lois.management.domain");
        try (InputStream input = getClass().getResourceAsStream("/mapper/" + mapper + ".xml")) {
            new XMLMapperBuilder(input, configuration, mapper + ".xml", configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement("com.lois.management.mapper." + mapper + "." + statement)
                .getBoundSql(parameter).getSql();
    }

    private CakeStockService transactionalService() {
        ProxyFactory factory = new ProxyFactory(new CakeStockService(stocks, movements, manager, events, true));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (CakeStockService) factory.getProxy();
    }

    private void assertRolledBack() {
        assertThat(manager.commits).isZero();
        assertThat(manager.rollbacks).isEqualTo(1);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    // Existing ReservationsServiceTest uses the same transaction-manager pattern;
    // its helper is private and cannot be reused directly.
    private static class CountingTransactionManager extends AbstractPlatformTransactionManager {
        int begins;
        int commits;
        int rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) { begins++; }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
