package org.eclipse.cargotracker.domain.model.cargo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackingIdTest {

    @Test
    void constructor_withValidId_createsTrackingId() {
        TrackingId trackingId = new TrackingId("ABC123");
        assertNotNull(trackingId);
        assertEquals("ABC123", trackingId.getIdString());
    }

    @Test
    void constructor_withNullId_throwsException() {
        assertThrows(NullPointerException.class, () -> new TrackingId(null));
    }

    @Test
    void defaultConstructor_createsEmptyTrackingId() {
        TrackingId trackingId = new TrackingId();
        assertNotNull(trackingId);
    }

    @Test
    void getIdString_returnsCorrectId() {
        TrackingId trackingId = new TrackingId("XYZ789");
        assertEquals("XYZ789", trackingId.getIdString());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        TrackingId trackingId = new TrackingId("ABC123");
        assertEquals(trackingId, trackingId);
    }

    @Test
    void equals_equalIds_returnsTrue() {
        TrackingId id1 = new TrackingId("ABC123");
        TrackingId id2 = new TrackingId("ABC123");
        assertEquals(id1, id2);
    }

    @Test
    void equals_differentIds_returnsFalse() {
        TrackingId id1 = new TrackingId("ABC123");
        TrackingId id2 = new TrackingId("XYZ789");
        assertNotEquals(id1, id2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        TrackingId trackingId = new TrackingId("ABC123");
        assertNotEquals(trackingId, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        TrackingId trackingId = new TrackingId("ABC123");
        assertNotEquals(trackingId, "ABC123");
    }

    @Test
    void hashCode_equalIds_sameHashCode() {
        TrackingId id1 = new TrackingId("ABC123");
        TrackingId id2 = new TrackingId("ABC123");
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void hashCode_differentIds_differentHashCode() {
        TrackingId id1 = new TrackingId("ABC123");
        TrackingId id2 = new TrackingId("XYZ789");
        assertNotEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void toString_returnsIdString() {
        TrackingId trackingId = new TrackingId("ABC123");
        assertEquals("ABC123", trackingId.toString());
    }

    @Test
    void sameValueAs_equalIds_returnsTrue() {
        TrackingId id1 = new TrackingId("ABC123");
        TrackingId id2 = new TrackingId("ABC123");
        assertTrue(id1.sameValueAs(id2));
    }

    @Test
    void sameValueAs_differentIds_returnsFalse() {
        TrackingId id1 = new TrackingId("ABC123");
        TrackingId id2 = new TrackingId("XYZ789");
        assertFalse(id1.sameValueAs(id2));
    }

    @Test
    void sameValueAs_nullOther_returnsFalse() {
        TrackingId id1 = new TrackingId("ABC123");
        assertFalse(id1.sameValueAs(null));
    }
}
