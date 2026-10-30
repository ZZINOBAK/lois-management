package com.lois.management.service;

import com.lois.management.mapper.CakeMovementMapper;
import com.lois.management.mapper.CakeStockMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CakeStockServiceTest {
    private final CakeStockMapper stocks = mock(CakeStockMapper.class);
    private final CakeMovementMapper movements = mock(CakeMovementMapper.class);
    private final CountingTransactionManager manager = new CountingTransactionManager();
    private final CakeStockService service = transactionalService();

    @Test
    void productionCommitsBothWritesInOrderWithGeneratedValues() {
        when(stocks.increaseStock(1L, 2)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1;
        });
        when(movements.insertProductionMovement(eq(1L), eq(2), eq("PRODUCED"), eq(true), anyString()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(UUID.fromString(invocation.getArgument(4, String.class)).toString())
                            .isEqualTo(invocation.getArgument(4, String.class));
                    return 1;
                });
        service.produce(1L, 2);
        var order = inOrder(stocks, movements);
        order.verify(stocks).increaseStock(1L, 2);
        order.verify(movements).insertProductionMovement(eq(1L), eq(2), eq("PRODUCED"), eq(true), anyString());
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
        when(movements.insertProductionMovement(anyLong(), anyInt(), anyString(), anyBoolean(), anyString()))
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
        String sql = sql("CakeMovementMapper", "insertProductionMovement",
                Map.of("cakeId", 1L, "cakeSize", 2, "moveType", "PRODUCED", "stockChanged", true, "requestId", "test"));
        assertThat(sql).contains("(cake_id, cake_size, move_type, stock_changed, request_id)")
                .doesNotContain("reservation_id", "delta", "biz_date");
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
        ProxyFactory factory = new ProxyFactory(new CakeStockService(stocks, movements));
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
