package org.eclipse.cargotracker.domain.model.cargo;

import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandlingActivityTest {

    private Location stockholm;
    private Location hamburg;
    private Voyage voyage;

    @BeforeEach
    void setUp() {
        stockholm = SampleLocations.STOCKHOLM;
        hamburg = SampleLocations.HAMBURG;
        voyage = SampleVoyages.CM001;
    }

    @Test
    void defaultConstructor_createsEmptyActivity() {
        HandlingActivity activity = new HandlingActivity();
        assertNotNull(activity);
        assertTrue(activity.isEmpty());
    }

    @Test
    void constructor_withTypeAndLocation_createsActivity() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertNotNull(activity);
        assertEquals(HandlingEvent.Type.RECEIVE, activity.getType());
        assertEquals(stockholm, activity.getLocation());
        assertNull(activity.getVoyage());
    }

    @Test
    void constructor_withTypeLocationAndVoyage_createsActivity() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.LOAD, stockholm, voyage);
        assertNotNull(activity);
        assertEquals(HandlingEvent.Type.LOAD, activity.getType());
        assertEquals(stockholm, activity.getLocation());
        assertEquals(voyage, activity.getVoyage());
    }

    @Test
    void constructor_withNullType_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingActivity(null, stockholm));
    }

    @Test
    void constructor_withNullLocation_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingActivity(HandlingEvent.Type.RECEIVE, null));
    }

    @Test
    void constructor_withVoyage_nullType_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingActivity(null, stockholm, voyage));
    }

    @Test
    void constructor_withVoyage_nullLocation_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingActivity(HandlingEvent.Type.LOAD, null, voyage));
    }

    @Test
    void constructor_withVoyage_nullVoyage_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new HandlingActivity(HandlingEvent.Type.LOAD, stockholm, null));
    }

    @Test
    void getType_returnsCorrectType() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.CLAIM, stockholm);
        assertEquals(HandlingEvent.Type.CLAIM, activity.getType());
    }

    @Test
    void getLocation_returnsCorrectLocation() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(stockholm, activity.getLocation());
    }

    @Test
    void getVoyage_withVoyage_returnsVoyage() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.LOAD, stockholm, voyage);
        assertEquals(voyage, activity.getVoyage());
    }

    @Test
    void getVoyage_withoutVoyage_returnsNull() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertNull(activity.getVoyage());
    }

    @Test
    void isEmpty_withAllFieldsSet_returnsFalse() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertFalse(activity.isEmpty());
    }

    @Test
    void isEmpty_defaultConstructor_returnsTrue() {
        HandlingActivity activity = new HandlingActivity();
        assertTrue(activity.isEmpty());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(activity, activity);
    }

    @Test
    void equals_equalActivities_returnsTrue() {
        HandlingActivity activity1 = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        HandlingActivity activity2 = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(activity1, activity2);
    }

    @Test
    void equals_differentType_returnsFalse() {
        HandlingActivity activity1 = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        HandlingActivity activity2 = new HandlingActivity(HandlingEvent.Type.CLAIM, stockholm);
        assertNotEquals(activity1, activity2);
    }

    @Test
    void equals_differentLocation_returnsFalse() {
        HandlingActivity activity1 = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        HandlingActivity activity2 = new HandlingActivity(HandlingEvent.Type.RECEIVE, hamburg);
        assertNotEquals(activity1, activity2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertNotEquals(activity, null);
    }

    @Test
    void equals_differentType_objectType_returnsFalse() {
        HandlingActivity activity = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertNotEquals(activity, "activity");
    }

    @Test
    void hashCode_equalActivities_sameHashCode() {
        HandlingActivity activity1 = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        HandlingActivity activity2 = new HandlingActivity(HandlingEvent.Type.RECEIVE, stockholm);
        assertEquals(activity1.hashCode(), activity2.hashCode());
    }
}
