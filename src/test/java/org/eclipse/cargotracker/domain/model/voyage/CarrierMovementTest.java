package org.eclipse.cargotracker.domain.model.voyage;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarrierMovementTest {

    private Location stockholm;
    private Location hamburg;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private CarrierMovement movement;

    @BeforeEach
    void setUp() {
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        departureTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        arrivalTime = LocalDateTime.of(2024, 1, 2, 18, 0, 0);
        movement = new CarrierMovement(stockholm, hamburg, departureTime, arrivalTime);
    }

    @Test
    void constructor_withValidArgs_createsCarrierMovement() {
        assertNotNull(movement);
        assertEquals(stockholm, movement.getDepartureLocation());
        assertEquals(hamburg, movement.getArrivalLocation());
    }

    @Test
    void constructor_withNullDepartureLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CarrierMovement(null, hamburg, departureTime, arrivalTime));
    }

    @Test
    void constructor_withNullArrivalLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CarrierMovement(stockholm, null, departureTime, arrivalTime));
    }

    @Test
    void constructor_withNullDepartureTime_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CarrierMovement(stockholm, hamburg, null, arrivalTime));
    }

    @Test
    void constructor_withNullArrivalTime_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CarrierMovement(stockholm, hamburg, departureTime, null));
    }

    @Test
    void defaultConstructor_createsEmptyMovement() {
        CarrierMovement cm = new CarrierMovement();
        assertNotNull(cm);
    }

    @Test
    void noneMovement_isNotNull() {
        assertNotNull(CarrierMovement.NONE);
    }

    @Test
    void getDepartureLocation_returnsCorrectLocation() {
        assertEquals(stockholm, movement.getDepartureLocation());
    }

    @Test
    void getArrivalLocation_returnsCorrectLocation() {
        assertEquals(hamburg, movement.getArrivalLocation());
    }

    @Test
    void getDepartureTime_returnsCorrectTime() {
        // Times are truncated to seconds
        assertEquals(departureTime.withNano(0), movement.getDepartureTime());
    }

    @Test
    void getArrivalTime_returnsCorrectTime() {
        // Times are truncated to seconds
        assertEquals(arrivalTime.withNano(0), movement.getArrivalTime());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        assertEquals(movement, movement);
    }

    @Test
    void equals_equalMovements_returnsTrue() {
        CarrierMovement movement2 = new CarrierMovement(stockholm, hamburg, departureTime, arrivalTime);
        assertEquals(movement, movement2);
    }

    @Test
    void equals_differentDepartureLocation_returnsFalse() {
        Location tokyo = SampleLocations.TOKYO;
        CarrierMovement movement2 = new CarrierMovement(tokyo, hamburg, departureTime, arrivalTime);
        assertNotEquals(movement, movement2);
    }

    @Test
    void equals_differentArrivalLocation_returnsFalse() {
        Location tokyo = SampleLocations.TOKYO;
        CarrierMovement movement2 = new CarrierMovement(stockholm, tokyo, departureTime, arrivalTime);
        assertNotEquals(movement, movement2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertNotEquals(movement, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        assertNotEquals(movement, "movement");
    }

    @Test
    void hashCode_equalMovements_sameHashCode() {
        CarrierMovement movement2 = new CarrierMovement(stockholm, hamburg, departureTime, arrivalTime);
        assertEquals(movement.hashCode(), movement2.hashCode());
    }
}
