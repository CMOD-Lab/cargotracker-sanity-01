package org.eclipse.cargotracker.domain.model.cargo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteSpecificationUnitTest {

    private Location stockholm;
    private Location hamburg;
    private LocalDate arrivalDeadline;
    private RouteSpecification spec;

    @BeforeEach
    void setUp() {
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        arrivalDeadline = LocalDate.of(2025, 12, 31);
        spec = new RouteSpecification(stockholm, hamburg, arrivalDeadline);
    }

    @Test
    void constructor_withValidArgs_createsRouteSpecification() {
        assertNotNull(spec);
        assertEquals(stockholm, spec.getOrigin());
        assertEquals(hamburg, spec.getDestination());
        assertEquals(arrivalDeadline, spec.getArrivalDeadline());
    }

    @Test
    void constructor_withNullOrigin_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new RouteSpecification(null, hamburg, arrivalDeadline));
    }

    @Test
    void constructor_withNullDestination_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new RouteSpecification(stockholm, null, arrivalDeadline));
    }

    @Test
    void constructor_withNullArrivalDeadline_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new RouteSpecification(stockholm, hamburg, null));
    }

    @Test
    void constructor_withSameOriginAndDestination_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteSpecification(stockholm, stockholm, arrivalDeadline));
    }

    @Test
    void defaultConstructor_createsEmptySpec() {
        RouteSpecification emptySpec = new RouteSpecification();
        assertNotNull(emptySpec);
    }

    @Test
    void getOrigin_returnsCorrectOrigin() {
        assertEquals(stockholm, spec.getOrigin());
    }

    @Test
    void getDestination_returnsCorrectDestination() {
        assertEquals(hamburg, spec.getDestination());
    }

    @Test
    void getArrivalDeadline_returnsCorrectDeadline() {
        assertEquals(arrivalDeadline, spec.getArrivalDeadline());
    }

    @Test
    void isSatisfiedBy_nullItinerary_returnsFalse() {
        assertFalse(spec.isSatisfiedBy(null));
    }

    @Test
    void isSatisfiedBy_validItinerary_returnsTrue() {
        Voyage voyage = SampleVoyages.CM001;
        Leg leg = new Leg(voyage, stockholm, hamburg,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2025, 6, 1, 18, 0, 0));
        Itinerary itinerary = new Itinerary(Arrays.asList(leg));
        assertTrue(spec.isSatisfiedBy(itinerary));
    }

    @Test
    void isSatisfiedBy_wrongOrigin_returnsFalse() {
        Location tokyo = SampleLocations.TOKYO;
        Voyage voyage = SampleVoyages.CM001;
        Leg leg = new Leg(voyage, tokyo, hamburg,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2025, 6, 1, 18, 0, 0));
        Itinerary itinerary = new Itinerary(Arrays.asList(leg));
        assertFalse(spec.isSatisfiedBy(itinerary));
    }

    @Test
    void isSatisfiedBy_wrongDestination_returnsFalse() {
        Location tokyo = SampleLocations.TOKYO;
        Voyage voyage = SampleVoyages.CM001;
        Leg leg = new Leg(voyage, stockholm, tokyo,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2025, 6, 1, 18, 0, 0));
        Itinerary itinerary = new Itinerary(Arrays.asList(leg));
        assertFalse(spec.isSatisfiedBy(itinerary));
    }

    @Test
    void isSatisfiedBy_arrivalAfterDeadline_returnsFalse() {
        Voyage voyage = SampleVoyages.CM001;
        Leg leg = new Leg(voyage, stockholm, hamburg,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2026, 1, 1, 18, 0, 0));
        Itinerary itinerary = new Itinerary(Arrays.asList(leg));
        assertFalse(spec.isSatisfiedBy(itinerary));
    }

    @Test
    void equals_sameValues_returnsTrue() {
        RouteSpecification spec2 = new RouteSpecification(stockholm, hamburg, arrivalDeadline);
        assertEquals(spec, spec2);
    }

    @Test
    void equals_differentOrigin_returnsFalse() {
        RouteSpecification spec2 = new RouteSpecification(SampleLocations.TOKYO, hamburg, arrivalDeadline);
        assertNotEquals(spec, spec2);
    }

    @Test
    void equals_differentDestination_returnsFalse() {
        RouteSpecification spec2 = new RouteSpecification(stockholm, SampleLocations.TOKYO, arrivalDeadline);
        assertNotEquals(spec, spec2);
    }

    @Test
    void equals_differentDeadline_returnsFalse() {
        RouteSpecification spec2 = new RouteSpecification(stockholm, hamburg, LocalDate.of(2026, 1, 1));
        assertNotEquals(spec, spec2);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        assertEquals(spec, spec);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertNotEquals(spec, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        assertNotEquals(spec, "spec");
    }

    @Test
    void hashCode_equalSpecs_sameHashCode() {
        RouteSpecification spec2 = new RouteSpecification(stockholm, hamburg, arrivalDeadline);
        assertEquals(spec.hashCode(), spec2.hashCode());
    }
}
