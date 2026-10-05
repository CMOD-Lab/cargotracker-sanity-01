package org.eclipse.cargotracker.domain.model.cargo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoutingStatusTest {

    @Test
    void notRouted_sameValueAs_notRouted_returnsTrue() {
        assertTrue(RoutingStatus.NOT_ROUTED.sameValueAs(RoutingStatus.NOT_ROUTED));
    }

    @Test
    void notRouted_sameValueAs_routed_returnsFalse() {
        assertFalse(RoutingStatus.NOT_ROUTED.sameValueAs(RoutingStatus.ROUTED));
    }

    @Test
    void routed_sameValueAs_routed_returnsTrue() {
        assertTrue(RoutingStatus.ROUTED.sameValueAs(RoutingStatus.ROUTED));
    }

    @Test
    void misrouted_sameValueAs_misrouted_returnsTrue() {
        assertTrue(RoutingStatus.MISROUTED.sameValueAs(RoutingStatus.MISROUTED));
    }

    @Test
    void allValues_areDistinct() {
        RoutingStatus[] values = RoutingStatus.values();
        assertEquals(3, values.length);
    }

    @Test
    void valueOf_notRouted_returnsCorrectEnum() {
        assertEquals(RoutingStatus.NOT_ROUTED, RoutingStatus.valueOf("NOT_ROUTED"));
    }

    @Test
    void valueOf_routed_returnsCorrectEnum() {
        assertEquals(RoutingStatus.ROUTED, RoutingStatus.valueOf("ROUTED"));
    }

    @Test
    void valueOf_misrouted_returnsCorrectEnum() {
        assertEquals(RoutingStatus.MISROUTED, RoutingStatus.valueOf("MISROUTED"));
    }
}
