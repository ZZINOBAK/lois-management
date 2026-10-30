package com.lois.management.service;

import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationUpdateReq;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.CakeStockMapper;
import com.lois.management.mapper.CakeMovementMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.ReservationLimiter;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReservationContactUpdateTest {
    private final ReservationMapper mapper = mock(ReservationMapper.class);
    private final ReservationService service = new ReservationService(
            mapper, mock(ReservationPolicyMapper.class), mock(ReservationLimiter.class), mock(CakeStockMapper.class), mock(CakeMovementMapper.class));

    @Test
    void webUpdateUsesLastFourCharactersAndWritesBothColumns() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setContact("010-1234-0056");
        reservation.setContactSuffix("9999");

        service.update(7L, reservation);

        assertThat(reservation.getContactSuffix()).isEqualTo("0056");
        verify(mapper).update(7L, reservation);
        assertThat(sql("update", Map.of("id", 7L, "reservation", reservation)))
                .contains("contact = ?", "contact_suffix = ?");
    }

    @Test
    void updateWithoutContactLeavesContactColumnsUntouched() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setNote("updated");
        service.update(7L, reservation);

        assertThat(sql("update", Map.of("id", 7L, "reservation", reservation)))
                .doesNotContain("contact =", "contact_suffix =");
        verify(mapper).update(7L, reservation);
    }

    @Test
    void apiContactUpdateUsesSameSuffixRule() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setContact("010-1234-9999");
        reservation.setContactSuffix("9999");
        when(mapper.findById(7L)).thenReturn(reservation);

        service.updateApi(7L, new ReservationUpdateReq(null, null, "010-1234-0056", null, null, null));

        assertThat(reservation.getContactSuffix()).isEqualTo("0056");
        verify(mapper).updateApi(reservation);
    }

    private String sql(String statement, Object parameter) throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.lois.management.domain");
        try (InputStream input = getClass().getResourceAsStream("/mapper/ReservationMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "ReservationMapper.xml", configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ReservationMapper.class.getName() + "." + statement)
                .getBoundSql(parameter).getSql();
    }
}
