package org.eclipse.cargotracker.domain.model.voyage;

import java.time.LocalDateTime;
import java.util.List;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoyageTest {

    private VoyageNumber voyageNumber;
    private Location stockholm;
    private Location hamburg;
    private Voyage voyage;

    @BeforeEach
    void setUp() {
        voyageNumber = new VoyageNumber("V100");
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        voyage = new Voyage.Builder(voyageNumber, stockholm)
                .addMovement(hamburg,
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2))
                .build();
    }

    @Test
    void constructor_withValidArgs_createsVoyage() {
        assertNotNull(voyage);
        assertEquals(voyageNumber, voyage.getVoyageNumber());
    }

    @Test
    void constructor_withNullVoyageNumber_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new Voyage(null, Schedule.EMPTY));
    }

    @Test
    void constructor_withNullSchedule_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new Voyage(voyageNumber, null));
    }

    @Test
    void defaultConstructor_createsEmptyVoyage() {
        Voyage v = new Voyage();
        assertNotNull(v);
    }

    @Test
    void noneVoyage_isNotNull() {
        assertNotNull(Voyage.NONE);
    }

    @Test
    void noneVoyage_hasEmptyVoyageNumber() {
        assertEquals("", Voyage.NONE.getVoyageNumber().getIdString());
    }

    @Test
    void getVoyageNumber_returnsCorrectNumber() {
        assertEquals(voyageNumber, voyage.getVoyageNumber());
    }

    @Test
    void getSchedule_returnsNotNull() {
        assertNotNull(voyage.getSchedule());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        assertEquals(voyage, voyage);
    }

    @Test
    void equals_sameVoyageNumber_returnsTrue() {
        Voyage voyage2 = new Voyage.Builder(voyageNumber, stockholm)
                .addMovement(hamburg,
                        LocalDateTime.now().plusDays(3),
                        LocalDateTime.now().plusDays(4))
                .build();
        assertEquals(voyage, voyage2);
    }

    @Test
    void equals_differentVoyageNumber_returnsFalse() {
        Voyage voyage2 = new Voyage.Builder(new VoyageNumber("V200"), stockholm)
                .addMovement(hamburg,
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2))
                .build();
        assertNotEquals(voyage, voyage2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        assertNotEquals(voyage, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        assertNotEquals(voyage, "V100");
    }

    @Test
    void hashCode_sameVoyageNumber_sameHashCode() {
        Voyage voyage2 = new Voyage.Builder(voyageNumber, stockholm)
                .addMovement(hamburg,
                        LocalDateTime.now().plusDays(3),
                        LocalDateTime.now().plusDays(4))
                .build();
        assertEquals(voyage.hashCode(), voyage2.hashCode());
    }

    @Test
    void toString_containsVoyageNumber() {
        assertTrue(voyage.toString().contains("V100"));
    }

    @Test
    void sameIdentityAs_sameVoyageNumber_returnsTrue() {
        Voyage voyage2 = new Voyage.Builder(voyageNumber, stockholm)
                .addMovement(hamburg,
                        LocalDateTime.now().plusDays(3),
                        LocalDateTime.now().plusDays(4))
                .build();
        assertTrue(voyage.sameIdentityAs(voyage2));
    }

    @Test
    void sameIdentityAs_differentVoyageNumber_returnsFalse() {
        Voyage voyage2 = new Voyage.Builder(new VoyageNumber("V999"), stockholm)
                .addMovement(hamburg,
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2))
                .build();
        assertFalse(voyage.sameIdentityAs(voyage2));
    }

    @Test
    void sameIdentityAs_nullOther_returnsFalse() {
        assertFalse(voyage.sameIdentityAs(null));
    }

    @Test
    void builder_addMultipleMovements_buildsCorrectly() {
        Location tokyo = SampleLocations.TOKYO;
        Voyage multiLegVoyage = new Voyage.Builder(new VoyageNumber("V300"), stockholm)
                .addMovement(hamburg,
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2))
                .addMovement(tokyo,
                        LocalDateTime.now().plusDays(3),
                        LocalDateTime.now().plusDays(10))
                .build();
        assertNotNull(multiLegVoyage);
        assertEquals(2, multiLegVoyage.getSchedule().getCarrierMovements().size());
    }

    @Test
    void builder_withNullVoyageNumber_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new Voyage.Builder(null, stockholm));
    }

    @Test
    void builder_withNullDepartureLocation_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new Voyage.Builder(voyageNumber, null));
    }
}
