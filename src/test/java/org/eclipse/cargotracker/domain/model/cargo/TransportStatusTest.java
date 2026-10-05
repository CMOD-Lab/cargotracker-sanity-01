package org.eclipse.cargotracker.domain.model.cargo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportStatusTest {

    @Test
    void notReceived_sameValueAs_notReceived_returnsTrue() {
        assertTrue(TransportStatus.NOT_RECEIVED.sameValueAs(TransportStatus.NOT_RECEIVED));
    }

    @Test
    void notReceived_sameValueAs_inPort_returnsFalse() {
        assertFalse(TransportStatus.NOT_RECEIVED.sameValueAs(TransportStatus.IN_PORT));
    }

    @Test
    void inPort_sameValueAs_inPort_returnsTrue() {
        assertTrue(TransportStatus.IN_PORT.sameValueAs(TransportStatus.IN_PORT));
    }

    @Test
    void onboardCarrier_sameValueAs_onboardCarrier_returnsTrue() {
        assertTrue(TransportStatus.ONBOARD_CARRIER.sameValueAs(TransportStatus.ONBOARD_CARRIER));
    }

    @Test
    void claimed_sameValueAs_claimed_returnsTrue() {
        assertTrue(TransportStatus.CLAIMED.sameValueAs(TransportStatus.CLAIMED));
    }

    @Test
    void unknown_sameValueAs_unknown_returnsTrue() {
        assertTrue(TransportStatus.UNKNOWN.sameValueAs(TransportStatus.UNKNOWN));
    }

    @Test
    void allValues_areDistinct() {
        TransportStatus[] values = TransportStatus.values();
        assertEquals(5, values.length);
    }

    @Test
    void valueOf_notReceived_returnsCorrectEnum() {
        assertEquals(TransportStatus.NOT_RECEIVED, TransportStatus.valueOf("NOT_RECEIVED"));
    }

    @Test
    void valueOf_inPort_returnsCorrectEnum() {
        assertEquals(TransportStatus.IN_PORT, TransportStatus.valueOf("IN_PORT"));
    }

    @Test
    void valueOf_onboardCarrier_returnsCorrectEnum() {
        assertEquals(TransportStatus.ONBOARD_CARRIER, TransportStatus.valueOf("ONBOARD_CARRIER"));
    }

    @Test
    void valueOf_claimed_returnsCorrectEnum() {
        assertEquals(TransportStatus.CLAIMED, TransportStatus.valueOf("CLAIMED"));
    }

    @Test
    void valueOf_unknown_returnsCorrectEnum() {
        assertEquals(TransportStatus.UNKNOWN, TransportStatus.valueOf("UNKNOWN"));
    }
}
