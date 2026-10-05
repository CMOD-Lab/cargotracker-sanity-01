package org.eclipse.cargotracker.domain.model.location;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationTest {

    private static final UnLocode NYC_CODE = new UnLocode("USNYC");
    private static final UnLocode HKG_CODE = new UnLocode("CNHKG");

    @Test
    void constructor_withValidArgs_createsLocation() {
        Location location = new Location(NYC_CODE, "New York");
        assertNotNull(location);
        assertEquals("New York", location.getName());
        assertEquals(NYC_CODE, location.getUnLocode());
    }

    @Test
    void constructor_withNullUnLocode_throwsException() {
        assertThrows(NullPointerException.class, () -> new Location(null, "New York"));
    }

    @Test
    void constructor_withNullName_throwsException() {
        assertThrows(NullPointerException.class, () -> new Location(NYC_CODE, null));
    }

    @Test
    void defaultConstructor_createsEmptyLocation() {
        Location location = new Location();
        assertNotNull(location);
    }

    @Test
    void getName_returnsCorrectName() {
        Location location = new Location(NYC_CODE, "New York");
        assertEquals("New York", location.getName());
    }

    @Test
    void getUnLocode_returnsCorrectCode() {
        Location location = new Location(NYC_CODE, "New York");
        assertEquals(NYC_CODE, location.getUnLocode());
    }

    @Test
    void unknownLocation_isNotNull() {
        assertNotNull(Location.UNKNOWN);
    }

    @Test
    void unknownLocation_hasUnknownCode() {
        assertEquals("XXXXX", Location.UNKNOWN.getUnLocode().getIdString());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        Location location = new Location(NYC_CODE, "New York");
        assertEquals(location, location);
    }

    @Test
    void equals_sameUnLocode_returnsTrue() {
        Location loc1 = new Location(NYC_CODE, "New York");
        Location loc2 = new Location(NYC_CODE, "New York City");
        assertEquals(loc1, loc2);
    }

    @Test
    void equals_differentUnLocode_returnsFalse() {
        Location loc1 = new Location(NYC_CODE, "New York");
        Location loc2 = new Location(HKG_CODE, "Hong Kong");
        assertNotEquals(loc1, loc2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        Location location = new Location(NYC_CODE, "New York");
        assertNotEquals(location, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        Location location = new Location(NYC_CODE, "New York");
        assertNotEquals(location, "New York");
    }

    @Test
    void sameIdentityAs_sameUnLocode_returnsTrue() {
        Location loc1 = new Location(NYC_CODE, "New York");
        Location loc2 = new Location(NYC_CODE, "New York City");
        assertTrue(loc1.sameIdentityAs(loc2));
    }

    @Test
    void sameIdentityAs_differentUnLocode_returnsFalse() {
        Location loc1 = new Location(NYC_CODE, "New York");
        Location loc2 = new Location(HKG_CODE, "Hong Kong");
        assertFalse(loc1.sameIdentityAs(loc2));
    }

    @Test
    void hashCode_sameUnLocode_sameHashCode() {
        Location loc1 = new Location(NYC_CODE, "New York");
        Location loc2 = new Location(NYC_CODE, "New York City");
        assertEquals(loc1.hashCode(), loc2.hashCode());
    }

    @Test
    void toString_containsNameAndCode() {
        Location location = new Location(NYC_CODE, "New York");
        String str = location.toString();
        assertTrue(str.contains("New York"));
        assertTrue(str.contains("USNYC"));
    }
}
