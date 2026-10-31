package com.lois.management.service;

import com.lois.management.dto.production.CakeProductionNeedDto;
import com.lois.management.mapper.CakeProductionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CakeProductionServiceTest {
    private final CakeProductionMapper mapper = mock(CakeProductionMapper.class);
    private final CakeProductionService service = new CakeProductionService(mapper);

    @ParameterizedTest
    @CsvSource({"4,2,2", "2,2,0", "1,3,0", "0,0,0", "2,0,2"})
    void calculatesRequiredQuantityForTodayAndSizeTwo(int reservations, int stock, int expected) {
        CakeProductionNeedDto row = new CakeProductionNeedDto();
        row.setCakeId(7L);
        row.setCakeFlavor("가나슈");
        row.setCakeSize(2);
        row.setReservationQuantity(reservations);
        row.setStockQuantity(stock);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(mapper.findProductionNeeds(today, 2)).thenReturn(List.of(row));

        var result = service.findTodayProductionNeeds();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRequiredQuantity()).isEqualTo(expected);
        assertThat(result.getFirst().getCakeId()).isEqualTo(7L);
        assertThat(result.getFirst().getCakeSize()).isEqualTo(2);
        verify(mapper).findProductionNeeds(today, 2);
    }

    @Test
    void preservesCakeOrder() {
        var first = new CakeProductionNeedDto();
        first.setCakeId(1L);
        var second = new CakeProductionNeedDto();
        second.setCakeId(3L);
        when(mapper.findProductionNeeds(any(), eq(2))).thenReturn(List.of(first, second));
        assertThat(service.findTodayProductionNeeds()).extracting(CakeProductionNeedDto::getCakeId)
                .containsExactly(1L, 3L);
    }

    @Test
    void noCakesReturnsEmptyList() {
        when(mapper.findProductionNeeds(any(), eq(2))).thenReturn(List.of());
        assertThat(service.findTodayProductionNeeds()).isEmpty();
    }

    @Test
    void queryFiltersTodayReservedSizeAndReadsCurrentQuantityWithoutDate() throws Exception {
        Configuration config = new Configuration();
        try (var input = getClass().getResourceAsStream("/mapper/CakeProductionMapper.xml")) {
            new XMLMapperBuilder(input, config, "CakeProductionMapper.xml", config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(CakeProductionMapper.class.getName() + ".findProductionNeeds");
        var bound = statement.getBoundSql(Map.of("today", LocalDate.of(2026, 10, 31), "cakeSize", 2));
        String sql = bound.getSql();
        assertThat(statement.getResultMaps().getFirst().getType()).isEqualTo(CakeProductionNeedDto.class);
        assertThat(sql).contains("LEFT JOIN", "COUNT(*)", "res_date = ?", "cake_size = ?",
                "make_status = 'RESERVED'", "ORDER BY c.id ASC", "COALESCE")
                .doesNotContain("pickup_status", "cake_movements");
        String stockQuery = sql.substring(sql.indexOf("SELECT s.quantity"), sql.indexOf("AS stockQuantity"));
        assertThat(stockQuery).contains("s.cake_id = c.id", "s.cake_size = ?").doesNotContain("date");
        assertThat(bound.getParameterMappings()).extracting("property")
                .containsExactly("cakeSize", "cakeSize", "today", "cakeSize");
    }
}
