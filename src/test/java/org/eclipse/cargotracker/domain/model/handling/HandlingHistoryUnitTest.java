package org.eclipse.cargotracker.domain.model.handling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

class HandlingHistoryUnitTest {

    private Cargo cargo;
    private Location stockholm;
    private Location hamburg;
    private Voyage voyage;

    @BeforeEach
    void setUp() {
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        voyage = SampleVoyages.CM001;

        TrackingId trackingId = new TrackingId("TEST001");
        RouteSpecification spec = new RouteSpecification(stockholm, hamburg,
                LocalDate.of(2025, 12, 31));
        cargo = new Cargo(trackingId, spec);
    }

    @Test
    void constructor_withValidEvents_createsHistory() {
        HandlingEvent event = new HandlingEvent(cargo,
                LocalDateTime.now(), LocalDateTime.now(),
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingHistory history = new HandlingHistory(Collections.singletonList(event));
        assertNotNull(history);
    }

    @Test
    void constructor_withNullEvents_throwsException() {
        assertThrows(NullPointerException.class, () -> new HandlingHistory(null));
    }

    @Test
    void emptyHistory_isNotNull() {
        assertNotNull(HandlingHistory.EMPTY);
    }

    @Test
    void emptyHistory_hasNoEvents() {
        assertTrue(HandlingHistory.EMPTY.getAllHandlingEvents().isEmpty());
    }

    @Test
    void getAllHandlingEvents_returnsAllEvents() {
        HandlingEvent event1 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingEvent event2 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                HandlingEvent.Type.LOAD, stockholm, voyage);

        HandlingHistory history = new HandlingHistory(Arrays.asList(event1, event2));
        assertEquals(2, history.getAllHandlingEvents().size());
    }

    @Test
    void getDistinctEventsByCompletionTime_returnsOrderedEvents() {
        HandlingEvent event1 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                HandlingEvent.Type.LOAD, stockholm, voyage);
        HandlingEvent event2 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                HandlingEvent.Type.RECEIVE, stockholm);

        HandlingHistory history = new HandlingHistory(Arrays.asList(event1, event2));
        List<HandlingEvent> ordered = history.getDistinctEventsByCompletionTime();

        assertEquals(2, ordered.size());
        assertEquals(event2, ordered.get(0));
        assertEquals(event1, ordered.get(1));
    }

    @Test
    void getMostRecentlyCompletedEvent_returnsLastEvent() {
        HandlingEvent event1 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingEvent event2 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                HandlingEvent.Type.LOAD, stockholm, voyage);

        HandlingHistory history = new HandlingHistory(Arrays.asList(event1, event2));
        assertEquals(event2, history.getMostRecentlyCompletedEvent());
    }

    @Test
    void getMostRecentlyCompletedEvent_emptyHistory_returnsNull() {
        assertNull(HandlingHistory.EMPTY.getMostRecentlyCompletedEvent());
    }

    @Test
    void equals_sameEvents_returnsTrue() {
        HandlingEvent event = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingHistory history1 = new HandlingHistory(Collections.singletonList(event));
        HandlingHistory history2 = new HandlingHistory(Collections.singletonList(event));
        assertEquals(history1, history2);
    }

    @Test
    void equals_differentEvents_returnsFalse() {
        HandlingEvent event1 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingEvent event2 = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0, 0),
                HandlingEvent.Type.LOAD, stockholm, voyage);

        HandlingHistory history1 = new HandlingHistory(Collections.singletonList(event1));
        HandlingHistory history2 = new HandlingHistory(Collections.singletonList(event2));
        assertNotEquals(history1, history2);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        HandlingHistory history = new HandlingHistory(Collections.emptyList());
        assertEquals(history, history);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        HandlingHistory history = new HandlingHistory(Collections.emptyList());
        assertNotEquals(history, null);
    }

    @Test
    void hashCode_equalHistories_sameHashCode() {
        HandlingEvent event = new HandlingEvent(cargo,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                HandlingEvent.Type.RECEIVE, stockholm);
        HandlingHistory history1 = new HandlingHistory(Collections.singletonList(event));
        HandlingHistory history2 = new HandlingHistory(Collections.singletonList(event));
        assertEquals(history1.hashCode(), history2.hashCode());
    }
}
