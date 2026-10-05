package org.eclipse.cargotracker.domain.model.handling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RouteSpecification;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandlingEventUnitTest {

    private Cargo cargo;
    private Location stockholm;
    private Location hamburg;
    private Voyage voyage;
    private LocalDateTime completionTime;
    private LocalDateTime registrationTime;

    @BeforeEach
    void setUp() {
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        voyage = SampleVoyages.CM001;

        TrackingId trackingId = new TrackingId("TEST001");
        RouteSpecification spec = new RouteSpecification(stockholm, hamburg,
                LocalDate.of(2025, 12, 31));
        cargo = new Cargo(trackingId, spec);

        completionTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        registrationTime = LocalDateTime.of(2024, 1, 1, 10, 5, 0);
    }

    @Test
    void constructor_withVoyage_createsHandlingEvent() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.LOAD, stockholm, voyage);
        assertNotNull(event);
        assertEquals(HandlingEvent.Type.LOAD, event.getType());
        assertEquals(stockholm, event.getLocation());
        assertEquals(cargo, event.getCargo());
    }

    @Test
    void constructor_withVoyage_nullCargo_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingEvent(null, completionTime, registrationTime,
                        HandlingEvent.Type.LOAD, stockholm, voyage));
    }

    @Test
    void constructor_withVoyage_nullCompletionTime_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingEvent(cargo, null, registrationTime,
                        HandlingEvent.Type.LOAD, stockholm, voyage));
    }

    @Test
    void constructor_withVoyage_nullRegistrationTime_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingEvent(cargo, completionTime, null,
                        HandlingEvent.Type.LOAD, stockholm, voyage));
    }

    @Test
    void constructor_withVoyage_nullType_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingEvent(cargo, completionTime, registrationTime,
                        null, stockholm, voyage));
    }

    @Test
    void constructor_withVoyage_nullLocation_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingEvent(cargo, completionTime, registrationTime,
                        HandlingEvent.Type.LOAD, null, voyage));
    }

    @Test
    void constructor_withVoyage_nullVoyage_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingEvent(cargo, completionTime, registrationTime,
                        HandlingEvent.Type.LOAD, stockholm, null));
    }

    @Test
    void constructor_withVoyage_typeProhibitsVoyage_throwsException() {
        // RECEIVE prohibits voyage
        assertThrows(IllegalArgumentException.class,
                () -> new HandlingEvent(cargo, completionTime, registrationTime,
                        HandlingEvent.Type.RECEIVE, stockholm, voyage));
    }

    @Test
    void constructor_withoutVoyage_createsHandlingEvent() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertNotNull(event);
        assertEquals(HandlingEvent.Type.RECEIVE, event.getType());
    }

    @Test
    void constructor_withoutVoyage_typeRequiresVoyage_throwsException() {
        // LOAD requires voyage
        assertThrows(IllegalArgumentException.class,
                () -> new HandlingEvent(cargo, completionTime, registrationTime,
                        HandlingEvent.Type.LOAD, stockholm));
    }

    @Test
    void defaultConstructor_createsEmptyEvent() {
        HandlingEvent event = new HandlingEvent();
        assertNotNull(event);
    }

    @Test
    void getType_returnsCorrectType() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(HandlingEvent.Type.RECEIVE, event.getType());
    }

    @Test
    void getVoyage_withVoyage_returnsVoyage() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.LOAD, stockholm, voyage);
        assertEquals(voyage, event.getVoyage());
    }

    @Test
    void getVoyage_withoutVoyage_returnsNoneVoyage() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertNotNull(event.getVoyage());
    }

    @Test
    void getCompletionTime_returnsCorrectTime() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(completionTime.withNano(0), event.getCompletionTime());
    }

    @Test
    void getRegistrationTime_returnsCorrectTime() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(registrationTime.withNano(0), event.getRegistrationTime());
    }

    @Test
    void getLocation_returnsCorrectLocation() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(stockholm, event.getLocation());
    }

    @Test
    void getCargo_returnsCorrectCargo() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(cargo, event.getCargo());
    }

    @Test
    void getSummary_containsLocationName() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        String summary = event.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Stockholm"));
    }

    @Test
    void getSummary_withVoyage_containsVoyageNumber() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.LOAD, stockholm, voyage);
        String summary = event.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Voyage"));
    }

    @Test
    void equals_sameEvent_returnsTrue() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(event, event);
    }

    @Test
    void equals_equalEvents_returnsTrue() {
        HandlingEvent event1 = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingEvent event2 = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(event1, event2);
    }

    @Test
    void equals_differentType_returnsFalse() {
        HandlingEvent event1 = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingEvent event2 = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.CLAIM, stockholm);
        assertNotEquals(event1, event2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertNotEquals(event, null);
    }

    @Test
    void hashCode_equalEvents_sameHashCode() {
        HandlingEvent event1 = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingEvent event2 = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void toString_containsCargoTrackingId() {
        HandlingEvent event = new HandlingEvent(cargo, completionTime, registrationTime,
                HandlingEvent.Type.RECEIVE, stockholm);
        String str = event.toString();
        assertNotNull(str);
        assertTrue(str.contains("TEST001"));
    }

    @Test
    void typeLoad_requiresVoyage() {
        assertTrue(HandlingEvent.Type.LOAD.requiresVoyage());
        assertFalse(HandlingEvent.Type.LOAD.prohibitsVoyage());
    }

    @Test
    void typeUnload_requiresVoyage() {
        assertTrue(HandlingEvent.Type.UNLOAD.requiresVoyage());
    }

    @Test
    void typeReceive_prohibitsVoyage() {
        assertFalse(HandlingEvent.Type.RECEIVE.requiresVoyage());
        assertTrue(HandlingEvent.Type.RECEIVE.prohibitsVoyage());
    }

    @Test
    void typeClaim_prohibitsVoyage() {
        assertTrue(HandlingEvent.Type.CLAIM.prohibitsVoyage());
    }

    @Test
    void typeCustoms_prohibitsVoyage() {
        assertTrue(HandlingEvent.Type.CUSTOMS.prohibitsVoyage());
    }

    @Test
    void typeSameValueAs_sameType_returnsTrue() {
        assertTrue(HandlingEvent.Type.LOAD.sameValueAs(HandlingEvent.Type.LOAD));
    }

    @Test
    void typeSameValueAs_differentType_returnsFalse() {
        assertFalse(HandlingEvent.Type.LOAD.sameValueAs(HandlingEvent.Type.UNLOAD));
    }

    @Test
    void typeSameValueAs_nullType_returnsFalse() {
        assertFalse(HandlingEvent.Type.LOAD.sameValueAs(null));
    }
}
