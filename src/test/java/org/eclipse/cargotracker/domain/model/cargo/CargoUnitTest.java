package org.eclipse.cargotracker.domain.model.cargo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingHistory;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CargoUnitTest {

    private TrackingId trackingId;
    private Location stockholm;
    private Location hamburg;
    private Location hongkong;
    private RouteSpecification routeSpec;
    private Cargo cargo;

    @BeforeEach
    void setUp() {
        trackingId = new TrackingId("TEST001");
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        hongkong = SampleLocations.HONGKONG;
        routeSpec = new RouteSpecification(stockholm, hamburg, LocalDate.of(2025, 12, 31));
        cargo = new Cargo(trackingId, routeSpec);
    }

    @Test
    void constructor_withValidArgs_createsCargo() {
        assertNotNull(cargo);
        assertEquals(trackingId, cargo.getTrackingId());
        assertEquals(stockholm, cargo.getOrigin());
        assertEquals(routeSpec, cargo.getRouteSpecification());
    }

    @Test
    void constructor_withNullTrackingId_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new Cargo(null, routeSpec));
    }

    @Test
    void constructor_withNullRouteSpec_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new Cargo(trackingId, null));
    }

    @Test
    void defaultConstructor_createsCargo() {
        Cargo emptyCargo = new Cargo();
        assertNotNull(emptyCargo);
    }

    @Test
    void getTrackingId_returnsCorrectId() {
        assertEquals(trackingId, cargo.getTrackingId());
    }

    @Test
    void getOrigin_returnsOriginFromRouteSpec() {
        assertEquals(stockholm, cargo.getOrigin());
    }

    @Test
    void getRouteSpecification_returnsCorrectSpec() {
        assertEquals(routeSpec, cargo.getRouteSpecification());
    }

    @Test
    void getItinerary_initiallyReturnsEmptyItinerary() {
        assertEquals(Itinerary.EMPTY_ITINERARY, cargo.getItinerary());
    }

    @Test
    void getDelivery_initiallyNotNull() {
        assertNotNull(cargo.getDelivery());
    }

    @Test
    void getDelivery_initiallyNotRouted() {
        assertEquals(RoutingStatus.NOT_ROUTED, cargo.getDelivery().getRoutingStatus());
    }

    @Test
    void getDelivery_initiallyNotReceived() {
        assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    }

    @Test
    void specifyNewRoute_updatesRouteSpecification() {
        RouteSpecification newSpec = new RouteSpecification(stockholm, hongkong,
                LocalDate.of(2025, 12, 31));
        cargo.specifyNewRoute(newSpec);
        assertEquals(newSpec, cargo.getRouteSpecification());
    }

    @Test
    void specifyNewRoute_withNullSpec_throwsException() {
        assertThrows(NullPointerException.class, () -> cargo.specifyNewRoute(null));
    }

    @Test
    void assignToRoute_updatesItinerary() {
        Voyage voyage = SampleVoyages.CM001;
        Leg leg = new Leg(voyage, stockholm, hamburg,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2025, 6, 1, 18, 0, 0));
        Itinerary itinerary = new Itinerary(Arrays.asList(leg));
        cargo.assignToRoute(itinerary);
        assertEquals(itinerary, cargo.getItinerary());
    }

    @Test
    void assignToRoute_withNullItinerary_throwsException() {
        assertThrows(NullPointerException.class, () -> cargo.assignToRoute(null));
    }

    @Test
    void assignToRoute_updatesDeliveryRoutingStatus() {
        Voyage voyage = SampleVoyages.CM001;
        Leg leg = new Leg(voyage, stockholm, hamburg,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2025, 6, 1, 18, 0, 0));
        Itinerary itinerary = new Itinerary(Arrays.asList(leg));
        cargo.assignToRoute(itinerary);
        assertEquals(RoutingStatus.ROUTED, cargo.getDelivery().getRoutingStatus());
    }

    @Test
    void deriveDeliveryProgress_withEmptyHistory_updatesDelivery() {
        cargo.deriveDeliveryProgress(HandlingHistory.EMPTY);
        assertNotNull(cargo.getDelivery());
    }

    @Test
    void setOrigin_updatesOrigin() {
        cargo.setOrigin(hamburg);
        assertEquals(hamburg, cargo.getOrigin());
    }

    @Test
    void equals_sameTrackingId_returnsTrue() {
        Cargo cargo2 = new Cargo(trackingId, routeSpec);
        assertEquals(cargo, cargo2);
    }

    @Test
    void equals_differentTrackingId_returnsFalse() {
        Cargo cargo2 = new Cargo(new TrackingId("OTHER001"), routeSpec);
        assertNotEquals(cargo, cargo2);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        assertEquals(cargo, cargo);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertNotEquals(cargo, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        assertNotEquals(cargo, "cargo");
    }

    @Test
    void hashCode_sameTrackingId_sameHashCode() {
        Cargo cargo2 = new Cargo(trackingId, routeSpec);
        assertEquals(cargo.hashCode(), cargo2.hashCode());
    }

    @Test
    void toString_returnsTrackingIdString() {
        assertEquals(trackingId.toString(), cargo.toString());
    }
}
