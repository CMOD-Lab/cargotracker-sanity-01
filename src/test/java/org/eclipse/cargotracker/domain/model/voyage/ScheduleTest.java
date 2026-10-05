package org.eclipse.cargotracker.domain.model.voyage;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleTest {

    private Location stockholm;
    private Location hamburg;
    private Location tokyo;
    private CarrierMovement movement1;
    private CarrierMovement movement2;

    @BeforeEach
    void setUp() {
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        tokyo = SampleLocations.TOKYO;

        movement1 = new CarrierMovement(stockholm, hamburg,
                LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                LocalDateTime.of(2024, 1, 2, 18, 0, 0));
        movement2 = new CarrierMovement(hamburg, tokyo,
                LocalDateTime.of(2024, 1, 3, 10, 0, 0),
                LocalDateTime.of(2024, 1, 10, 18, 0, 0));
    }

    @Test
    void defaultConstructor_createsEmptySchedule() {
        Schedule schedule = new Schedule();
        assertNotNull(schedule);
    }

    @Test
    void emptySchedule_isNotNull() {
        assertNotNull(Schedule.EMPTY);
    }

    @Test
    void getCarrierMovements_returnsUnmodifiableList() {
        Schedule schedule = new Schedule(Arrays.asList(movement1, movement2));
        List<CarrierMovement> movements = schedule.getCarrierMovements();
        assertThrows(UnsupportedOperationException.class, () -> movements.add(movement1));
    }

    @Test
    void getCarrierMovements_returnsCorrectMovements() {
        Schedule schedule = new Schedule(Arrays.asList(movement1, movement2));
        List<CarrierMovement> movements = schedule.getCarrierMovements();
        assertEquals(2, movements.size());
        assertEquals(movement1, movements.get(0));
        assertEquals(movement2, movements.get(1));
    }

    @Test
    void equals_sameMovements_returnsTrue() {
        Schedule schedule1 = new Schedule(Arrays.asList(movement1, movement2));
        Schedule schedule2 = new Schedule(Arrays.asList(movement1, movement2));
        assertEquals(schedule1, schedule2);
    }

    @Test
    void equals_differentMovements_returnsFalse() {
        Schedule schedule1 = new Schedule(Collections.singletonList(movement1));
        Schedule schedule2 = new Schedule(Collections.singletonList(movement2));
        assertNotEquals(schedule1, schedule2);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        Schedule schedule = new Schedule(Arrays.asList(movement1));
        assertEquals(schedule, schedule);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        Schedule schedule = new Schedule(Arrays.asList(movement1));
        assertNotEquals(schedule, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        Schedule schedule = new Schedule(Arrays.asList(movement1));
        assertNotEquals(schedule, "schedule");
    }

    @Test
    void hashCode_equalSchedules_sameHashCode() {
        Schedule schedule1 = new Schedule(Arrays.asList(movement1, movement2));
        Schedule schedule2 = new Schedule(Arrays.asList(movement1, movement2));
        assertEquals(schedule1.hashCode(), schedule2.hashCode());
    }
}
