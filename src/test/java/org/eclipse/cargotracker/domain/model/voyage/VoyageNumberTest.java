package org.eclipse.cargotracker.domain.model.voyage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoyageNumberTest {

    @Test
    void constructor_withValidNumber_createsVoyageNumber() {
        VoyageNumber voyageNumber = new VoyageNumber("V100");
        assertNotNull(voyageNumber);
        assertEquals("V100", voyageNumber.getIdString());
    }

    @Test
    void constructor_withNullNumber_throwsException() {
        assertThrows(NullPointerException.class, () -> new VoyageNumber(null));
    }

    @Test
    void constructor_withEmptyNumber_createsVoyageNumber() {
        // Empty string is allowed (used for NONE voyage)
        VoyageNumber voyageNumber = new VoyageNumber("");
        assertNotNull(voyageNumber);
    }

    @Test
    void defaultConstructor_createsEmptyVoyageNumber() {
        VoyageNumber voyageNumber = new VoyageNumber();
        assertNotNull(voyageNumber);
    }

    @Test
    void getIdString_returnsCorrectNumber() {
        VoyageNumber voyageNumber = new VoyageNumber("CM001");
        assertEquals("CM001", voyageNumber.getIdString());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        VoyageNumber voyageNumber = new VoyageNumber("V100");
        assertEquals(voyageNumber, voyageNumber);
    }

    @Test
    void equals_equalNumbers_returnsTrue() {
        VoyageNumber num1 = new VoyageNumber("V100");
        VoyageNumber num2 = new VoyageNumber("V100");
        assertEquals(num1, num2);
    }

    @Test
    void equals_differentNumbers_returnsFalse() {
        VoyageNumber num1 = new VoyageNumber("V100");
        VoyageNumber num2 = new VoyageNumber("V200");
        assertNotEquals(num1, num2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        VoyageNumber voyageNumber = new VoyageNumber("V100");
        assertNotEquals(voyageNumber, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        VoyageNumber voyageNumber = new VoyageNumber("V100");
        assertNotEquals(voyageNumber, "V100");
    }

    @Test
    void hashCode_equalNumbers_sameHashCode() {
        VoyageNumber num1 = new VoyageNumber("V100");
        VoyageNumber num2 = new VoyageNumber("V100");
        assertEquals(num1.hashCode(), num2.hashCode());
    }

    @Test
    void toString_returnsNumber() {
        VoyageNumber voyageNumber = new VoyageNumber("V100");
        assertEquals("V100", voyageNumber.toString());
    }

    @Test
    void sameValueAs_equalNumbers_returnsTrue() {
        VoyageNumber num1 = new VoyageNumber("V100");
        VoyageNumber num2 = new VoyageNumber("V100");
        assertTrue(num1.sameValueAs(num2));
    }

    @Test
    void sameValueAs_differentNumbers_returnsFalse() {
        VoyageNumber num1 = new VoyageNumber("V100");
        VoyageNumber num2 = new VoyageNumber("V200");
        assertFalse(num1.sameValueAs(num2));
    }

    @Test
    void sameValueAs_nullOther_returnsFalse() {
        VoyageNumber num1 = new VoyageNumber("V100");
        assertFalse(num1.sameValueAs(null));
    }
}
