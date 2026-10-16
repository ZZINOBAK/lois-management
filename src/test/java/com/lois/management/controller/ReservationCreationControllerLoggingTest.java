package com.lois.management.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lois.management.controller.api.ReservationApiController;
import com.lois.management.domain.Reservation;
import com.lois.management.dto.reservation.ReservationCreateReq;
import com.lois.management.service.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.support.SimpleSessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationCreationControllerLoggingTest {
    ReservationService service = mock(ReservationService.class);
    ReservationController web = new ReservationController(service, mock(CakeMovementService.class), mock(CakeService.class));
    ReservationApiController api = new ReservationApiController(service, mock(CakeMovementService.class));
    Logger webLogger = (Logger) LoggerFactory.getLogger(ReservationController.class);
    Logger apiLogger = (Logger) LoggerFactory.getLogger(ReservationApiController.class);
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    ReservationCreateReq req = new ReservationCreateReq(3L, "2026-10-16", "15:30", "PRIVATE_CONTACT", true);
    @BeforeEach void attach() { logs.start(); webLogger.addAppender(logs); apiLogger.addAppender(logs); }
    @AfterEach void detach() { webLogger.detachAppender(logs); apiLogger.detachAppender(logs); logs.stop(); }
    String output() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b) -> a + "\n" + b); }

    @Test void apiResponseAndBoundaryLogs() {
        when(service.create(req)).thenReturn(72L);
        var response = api.create(req);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/reservations/72");
        assertThat(response.getBody().getStatus()).isEqualTo("CREATED");
        assertThat(output()).contains("event=업무시작 entry=api:create", "event=업무처리종료 entry=api:create id=72 commit=미확인").doesNotContain("PRIVATE_CONTACT");
    }
    @Test void commitBoundaryFailureDoesNotLogCompletion() {
        var failure = new TransactionSystemException("PRIVATE_MESSAGE");
        when(service.create(req)).thenThrow(failure);
        assertThatThrownBy(() -> api.create(req)).isSameAs(failure);
        assertThat(output()).contains("commit=미확인").doesNotContain("업무처리종료", "PRIVATE_MESSAGE");
        assertThat(logs.list.getLast().getThrowableProxy()).isNull();
    }
    @Test void finishPreservesSessionAndRedirect() {
        Reservation r = new Reservation(); r.setId(72L);
        SimpleSessionStatus status = new SimpleSessionStatus();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        assertThat(web.finish(r, status, redirect)).isEqualTo("redirect:/reservations");
        assertThat(status.isComplete()).isTrue();
        assertThat(redirect.getFlashAttributes().get("resvDone")).isEqualTo(true);
        verify(service).create(r);
        assertThat(output()).contains("entry=web:finish", "id=72 commit=미확인");
    }
    @Test void webFailurePreservesExceptionAndSession() {
        Reservation r = new Reservation(); SimpleSessionStatus status = new SimpleSessionStatus();
        var failure = new IllegalArgumentException("PRIVATE_MESSAGE");
        doThrow(failure).when(service).create(r);
        assertThatThrownBy(() -> web.finish(r, status, new RedirectAttributesModelMap())).isSameAs(failure);
        assertThat(status.isComplete()).isFalse();
        assertThat(output()).doesNotContain("업무처리종료", "PRIVATE_MESSAGE");
    }
}
