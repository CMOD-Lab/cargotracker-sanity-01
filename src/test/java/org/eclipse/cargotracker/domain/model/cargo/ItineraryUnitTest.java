package org.eclipse.cargotracker.domain.model.cargo;

import java.time.LocalDate; 
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItineraryUnitTest {

    private Voyage voyage;
    private Location stockholm;
    private Location hamburg;
    private Location hongkong;
    private Leg leg1;
    private Leg leg2;
    private Itinerary itinerary;

    @BeforeEach
    void setUp() {
        voyage = SampleVoyages.CM001;
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        hongkong = SampleLocations.HONGKONG;

        leg1 = new Leg(voyage, stockholm, hamburg,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 2, 18, 0, 0));
        leg2 = new Leg(SampleVoyages.CM002, hamburg, hongkong,
                LocalDateTime.of(2024, 1, 3, 10, 0, 0),
                LocalDateTime.of(2024, 1, 10, 18, 0, 0));

        itinerary = new Itinerary(Arrays.asList(leg1, leg2));
    }

    @Test
    void constructor_withValidLegs_createsItinerary() {
        assertNotNull(itinerary);
        assertEquals(2, itinerary.getLegs().size());
    }

    @Test
    void constructor_withNullLegs_throwsException() {
        assertThrows(NullPointerException.class, () -> new Itinerary(null));
    }

    @Test
    void constructor_withEmptyLegs_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Itinerary(Collections.emptyList()));
    }

    @Test
    void constructor_withLegsContainingNull_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Itinerary(Arrays.asList(leg1, null)));
    }

    @Test
    void defaultConstructor_createsEmptyItinerary() {
        Itinerary emptyItinerary = new Itinerary();
        assertNotNull(emptyItinerary);
    }

    @Test
    void emptyItinerary_isNotNull() {
        assertNotNull(Itinerary.EMPTY_ITINERARY);
    }

    @Test
    void getLegs_returnsUnmodifiableList() {
        List<Leg> legs = itinerary.getLegs();
        assertThrows(UnsupportedOperationException.class, () -> legs.add(leg1));
    }

    @Test
    void getLegs_returnsCorrectLegs() {
        List<Leg> legs = itinerary.getLegs();
        assertEquals(2, legs.size());
        assertEquals(leg1, legs.get(0));
        assertEquals(leg2, legs.get(1));
    }

    @Test
    void getInitialDepartureLocation_returnsFirstLegLoadLocation() {
        assertEquals(stockholm, itinerary.getInitialDepartureLocation());
    }

    @Test
    void getInitialDepartureLocation_emptyItinerary_returnsUnknown() {
        Itinerary empty = new Itinerary();
        assertEquals(Location.UNKNOWN, empty.getInitialDepartureLocation());
    }

    @Test
    void getFinalArrivalLocation_returnsLastLegUnloadLocation() {
        assertEquals(hongkong, itinerary.getFinalArrivalLocation());
    }

    @Test
    void getFinalArrivalLocation_emptyItinerary_returnsUnknown() {
        Itinerary empty = new Itinerary();
        assertEquals(Location.UNKNOWN, empty.getFinalArrivalLocation());
    }

    @Test
    void getFinalArrivalDate_returnsLastLegUnloadTime() {
        assertEquals(leg2.getUnloadTime(), itinerary.getFinalArrivalDate());
    }

    @Test
    void getFinalArrivalDate_emptyItinerary_returnsMaxDateTime() {
        Itinerary empty = new Itinerary();
        assertEquals(LocalDateTime.MAX, empty.getFinalArrivalDate());
    }

    @Test
    void getLastLeg_returnsLastLeg() {
        assertEquals(leg2, itinerary.getLastLeg());
    }

    @Test
    void getLastLeg_emptyItinerary_returnsNull() {
        Itinerary empty = new Itinerary();
        assertNull(empty.getLastLeg());
    }

    @Test
    void isExpected_receiveEventAtFirstLeg_returnsTrue() {
        TrackingId trackingId = new TrackingId("TEST001");
        RouteSpecification spec = new RouteSpecification(stockholm, hongkong,
                LocalDate.of(2024, 12, 31));
        Cargo cargo = new Cargo(trackingId, spec);

        HandlingEvent receiveEvent = new HandlingEvent(cargo,
                LocalDateTime.now(), LocalDateTime.now(),
                HandlingEvent.Type.RECEIVE, stockholm);
        assertTrue(itinerary.isExpected(receiveEvent));
    }

    @Test
    void isExpected_receiveEventAtWrongLocation_returnsFalse() {
        TrackingId trackingId = new TrackingId("TEST001");
        RouteSpecification spec = new RouteSpecification(stockholm, hongkong,
                LocalDate.of(2024, 12, 31));
        Cargo cargo = new Cargo(trackingId, spec);

        HandlingEvent receiveEvent = new HandlingEvent(cargo,
                LocalDateTime.now(), LocalDateTime.now(),
                HandlingEvent.Type.RECEIVE, hamburg);
        assertFalse(itinerary.isExpected(receiveEvent));
    }

    @Test
    void isExpected_loadEventOnCorrectVoyage_returnsTrue() {
        TrackingId trackingId = new TrackingId("TEST001");
        RouteSpecification spec = new RouteSpecification(stockholm, hongkong,
                LocalDate.of(2024, 12, 31));
        Cargo cargo = new Cargo(trackingId, spec);

        HandlingEvent loadEvent = new HandlingEvent(cargo,
                LocalDateTime.now(), LocalDateTime.now(),
                HandlingEvent.Type.LOAD, stockholm, voyage);
        assertTrue(itinerary.isExpected(loadEvent));
    }

    @Test
    void isExpected_claimEventAtLastLeg_returnsTrue() {
        TrackingId trackingId = new TrackingId("TEST001");
        RouteSpecification spec = new RouteSpecification(stockholm, hongkong,
                LocalDate.of(2024, 12, 31));
        Cargo cargo = new Cargo(trackingId, spec);

        HandlingEvent claimEvent = new HandlingEvent(cargo,
                LocalDateTime.now(), LocalDateTime.now(),
                HandlingEvent.Type.CLAIM, hongkong);
        assertTrue(itinerary.isExpected(claimEvent));
    }

    @Test
    void isExpected_customsEvent_returnsTrue() {
        TrackingId trackingId = new TrackingId("TEST001");
        RouteSpecification spec = new RouteSpecification(stockholm, hongkong,
                LocalDate.of(2024, 12, 31));
        Cargo cargo = new Cargo(trackingId, spec);

        HandlingEvent customsEvent = new HandlingEvent(cargo,
                LocalDateTime.now(), LocalDateTime.now(),
                HandlingEvent.Type.CUSTOMS, stockholm);
        assertTrue(itinerary.isExpected(customsEvent));
    }

    @Test
    void equals_sameLegs_returnsTrue() {
        Itinerary itinerary2 = new Itinerary(Arrays.asList(leg1, leg2));
        assertEquals(itinerary, itinerary2);
    }

    @Test
    void equals_differentLegs_returnsFalse() {
        Itinerary itinerary2 = new Itinerary(Collections.singletonList(leg1));
        assertNotEquals(itinerary, itinerary2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertNotEquals(itinerary, null);
    }

    @Test
    void hashCode_equalItineraries_sameHashCode() {
        Itinerary itinerary2 = new Itinerary(Arrays.asList(leg1, leg2));
        assertEquals(itinerary.hashCode(), itinerary2.hashCode());
    }

    @Test
    void toString_containsLegsInfo() {
        String str = itinerary.toString();
        assertNotNull(str);
        assertTrue(str.contains("Itinerary"));
    }
}
