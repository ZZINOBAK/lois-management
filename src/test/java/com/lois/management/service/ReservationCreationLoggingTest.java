package com.lois.management.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationCreateReq;
import com.lois.management.mapper.ReservationMapper;
import com.lois.management.mapper.CakeStockMapper;
import com.lois.management.mapper.CakeMovementMapper;
import com.lois.management.mapper.ReservationPolicyMapper;
import com.lois.management.service.reservation.limiter.ReservationLimiter;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationCreationLoggingTest {
    ReservationMapper mapper = mock(ReservationMapper.class);
    ReservationLimiter limiter = mock(ReservationLimiter.class);
    ReservationService service = new ReservationService(mapper, mock(ReservationPolicyMapper.class), limiter, mock(CakeStockMapper.class), mock(CakeMovementMapper.class));
    Logger logger = (Logger) LoggerFactory.getLogger(ReservationService.class);
    ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach void attach() { logs.start(); logger.addAppender(logs); }
    @AfterEach void detach() { logger.detachAppender(logs); logs.stop(); }

    Reservation reservation() {
        Reservation r = new Reservation();
        r.setResDate(LocalDate.of(2026, 10, 16)); r.setResTime(LocalTime.of(15, 30));
        r.setCakeId(3L); r.setCakeSize(2); r.setPaid(true);
        r.setContact("010-9876-5432"); r.setNote("PRIVATE_NOTE");
        return r;
    }
    String output() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b) -> a + "\n" + b); }
    void safe() {
        assertThat(output()).doesNotContain("010-9876-5432", "5432", "PRIVATE_NOTE", "SECRET_EXCEPTION", "커밋 완료", "저장 성공");
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
    }

    @Test void insertLogsOnlySqlFieldsAndGeneratedIdWithoutSelect() {
        Reservation r = reservation();
        when(limiter.tryAcquireSlot(r.getResDate(), r.getResTime())).thenReturn(true);
        when(mapper.insert(r)).thenAnswer(i -> { r.setId(72L); return 1; });
        service.create(r);
        assertThat(output()).contains("res_date=2026-10-16 res_time=15:30 cake_id=3 paid=true", "id=72 affectedRows=1 commit=미확인").doesNotContain("cake_size", "make_status", "pickup_status");
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.INFO));
        verify(mapper).insert(r); verifyNoMoreInteractions(mapper); safe();
    }
    @Test void zeroRowsDoesNotChangeReturnOrInventId() {
        Reservation r = reservation();
        when(limiter.tryAcquireSlot(any(), any())).thenReturn(true);
        service.create(r);
        assertThat(output()).contains("id=null affectedRows=0", "event=반환행수확인");
        verify(mapper).insert(r); verifyNoMoreInteractions(mapper); safe();
    }
    @Test void sqlFailurePreservesExceptionAndDoesNotLogDbReturn() {
        Reservation r = reservation(); RuntimeException failure = new IllegalStateException("SECRET_EXCEPTION PRIVATE_NOTE");
        when(limiter.tryAcquireSlot(any(), any())).thenReturn(true);
        when(mapper.insert(r)).thenThrow(failure);
        assertThatThrownBy(() -> service.create(r)).isSameAs(failure);
        assertThat(output()).doesNotContain("event=DB처리반환");
        verify(mapper).insert(r); verifyNoMoreInteractions(mapper); safe();
    }
    @Test void limiterRejectionDoesNotInsert() {
        assertThatThrownBy(() -> service.create(reservation())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper); safe();
    }
    @Test void limiterFailureIsNotReportedAsSqlFailure() {
        RuntimeException failure = new IllegalStateException("SECRET_EXCEPTION");
        when(limiter.tryAcquireSlot(any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.create(reservation())).isSameAs(failure);
        assertThat(output()).doesNotContain("stage=ReservationMapper.insert");
        verifyNoInteractions(mapper); safe();
    }
    @Test void apiConversionFailureDoesNotExposeInput() {
        assertThatThrownBy(() -> service.create(new ReservationCreateReq(3L, "PRIVATE_NOTE", "15:30", "010-9876-5432", true))).isInstanceOf(java.time.format.DateTimeParseException.class);
        verifyNoInteractions(mapper, limiter); safe();
    }
    @Test void onSiteLogsEveryNonSensitiveSqlField() {
        Reservation r = reservation();
        when(mapper.insertOnSite(r)).thenAnswer(i -> { r.setId(73L); return 1; });
        service.createOnSite(r);
        assertThat(output()).contains("stage=ReservationMapper.insertOnSite res_date=" + r.getResDate(), "res_time=" + r.getResTime(), "cake_id=3 cake_size=2 make_status=READY paid=true pickup_status=PICKED", "id=73 affectedRows=1").doesNotContain("ON_SITE");
        verify(mapper).insertOnSite(r); verifyNoMoreInteractions(mapper); verifyNoInteractions(limiter); safe();
    }
    @Test void onSiteFailurePreservesException() {
        Reservation r = reservation(); RuntimeException failure = new IllegalStateException("SECRET_EXCEPTION");
        when(mapper.insertOnSite(r)).thenThrow(failure);
        assertThatThrownBy(() -> service.createOnSite(r)).isSameAs(failure);
        verify(mapper).insertOnSite(r); verifyNoMoreInteractions(mapper); safe();
    }
}
