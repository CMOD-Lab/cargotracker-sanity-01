package org.eclipse.cargotracker.domain.model.cargo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegTest {

    private Voyage voyage;
    private Location loadLocation;
    private Location unloadLocation;
    private LocalDateTime loadTime;
    private LocalDateTime unloadTime;
    private Leg leg;

    @BeforeEach
    void setUp() {
        voyage = SampleVoyages.CM001;
        loadLocation = SampleLocations.STOCKHOLM;
        unloadLocation = SampleLocations.HAMBURG;
        loadTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        unloadTime = LocalDateTime.of(2024, 1, 2, 18, 0, 0);
        leg = new Leg(voyage, loadLocation, unloadLocation, loadTime, unloadTime);
    }

    @Test
    void constructor_withValidArgs_createsLeg() {
        assertNotNull(leg);
        assertEquals(voyage, leg.getVoyage());
        assertEquals(loadLocation, leg.getLoadLocation());
        assertEquals(unloadLocation, leg.getUnloadLocation());
    }

    @Test
    void constructor_withNullVoyage_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg(null, loadLocation, unloadLocation, loadTime, unloadTime));
    }

    @Test
    void constructor_withNullLoadLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg(voyage, null, unloadLocation, loadTime, unloadTime));
    }

    @Test
    void constructor_withNullUnloadLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg(voyage, loadLocation, null, loadTime, unloadTime));
    }

    @Test
    void constructor_withNullLoadTime_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg(voyage, loadLocation, unloadLocation, null, unloadTime));
    }

    @Test
    void constructor_withNullUnloadTime_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg(voyage, loadLocation, unloadLocation, loadTime, null));
    }

    @Test
    void defaultConstructor_createsEmptyLeg() {
        Leg emptyLeg = new Leg();
        assertNotNull(emptyLeg);
    }

    @Test
    void getVoyage_returnsCorrectVoyage() {
        assertEquals(voyage, leg.getVoyage());
    }

    @Test
    void getLoadLocation_returnsCorrectLocation() {
        assertEquals(loadLocation, leg.getLoadLocation());
    }

    @Test
    void getUnloadLocation_returnsCorrectLocation() {
        assertEquals(unloadLocation, leg.getUnloadLocation());
    }

    @Test
    void getLoadTime_returnsCorrectTime() {
        assertEquals(loadTime.withNano(0), leg.getLoadTime());
    }

    @Test
    void getUnloadTime_returnsCorrectTime() {
        assertEquals(unloadTime.withNano(0), leg.getUnloadTime());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        assertEquals(leg, leg);
    }

    @Test
    void equals_equalLegs_returnsTrue() {
        Leg leg2 = new Leg(voyage, loadLocation, unloadLocation, loadTime, unloadTime);
        assertEquals(leg, leg2);
    }

    @Test
    void equals_differentVoyage_returnsFalse() {
        Leg leg2 = new Leg(SampleVoyages.CM002, loadLocation, unloadLocation, loadTime, unloadTime);
        assertNotEquals(leg, leg2);
    }

    @Test
    void equals_differentLoadLocation_returnsFalse() {
        Leg leg2 = new Leg(voyage, SampleLocations.TOKYO, unloadLocation, loadTime, unloadTime);
        assertNotEquals(leg, leg2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertNotEquals(leg, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        assertNotEquals(leg, "leg");
    }

    @Test
    void hashCode_equalLegs_sameHashCode() {
        Leg leg2 = new Leg(voyage, loadLocation, unloadLocation, loadTime, unloadTime);
        assertEquals(leg.hashCode(), leg2.hashCode());
    }

    @Test
    void toString_containsLegInfo() {
        String str = leg.toString();
        assertNotNull(str);
        assertTrue(str.contains("Leg"));
    }
}
