package com.lois.management.controller;

import com.lois.management.domain.Reservation;
import com.lois.management.domain.cake.controller.CakeMovementController;
import com.lois.management.domain.cake.service.CakeMovementService;
import com.lois.management.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CakeMovementControllerTest {
    private final ReservationService reservations = mock(ReservationService.class);
    private final CakeMovementService movements = mock(CakeMovementService.class);

    @Test
    void legacyProductionPostRemainsAvailable() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CakeMovementController(reservations, movements)).build();
        mvc.perform(post("/cake-movements/produce").param("cakeId", "1").param("cakeSize", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reservations"));
        verify(movements).pageProduce(any(Reservation.class), anyString(), isNull());
    }

    @Test
    void legacyOnSitePostRemainsAvailable() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CakeMovementController(reservations, movements)).build();
        mvc.perform(post("/cake-movements/on-site").param("cakeId", "1").param("cakeSize", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reservations"));
        verify(movements).sellOnSiteWithReservationAdjust(1L, 2, null);
    }
}
