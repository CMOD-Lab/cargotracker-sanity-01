package org.eclipse.cargotracker.domain.model.location;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnLocodeTest {

    @Test
    void constructor_withValidCode_createsUnLocode() {
        UnLocode unLocode = new UnLocode("USNYC");
        assertNotNull(unLocode);
        assertEquals("USNYC", unLocode.getIdString());
    }

    @Test
    void constructor_withLowerCaseCode_convertsToUpperCase() {
        UnLocode unLocode = new UnLocode("usnyc");
        assertEquals("USNYC", unLocode.getIdString());
    }

    @Test
    void constructor_withMixedCaseCode_convertsToUpperCase() {
        UnLocode unLocode = new UnLocode("UsNyC");
        assertEquals("USNYC", unLocode.getIdString());
    }

    @Test
    void constructor_withNullCode_throwsException() {
        assertThrows(NullPointerException.class, () -> new UnLocode(null));
    }

    @Test
    void constructor_withInvalidPattern_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UnLocode("1SNYC"));
    }

    @Test
    void constructor_withTooShortCode_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UnLocode("USNY"));
    }

    @Test
    void constructor_withTooLongCode_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UnLocode("USNYCX"));
    }

    @Test
    void constructor_withCodeContainingNumbers_isValid() {
        // Location code can contain numbers 2-9
        UnLocode unLocode = new UnLocode("US2YC");
        assertNotNull(unLocode);
    }

    @Test
    void defaultConstructor_createsEmptyUnLocode() {
        UnLocode unLocode = new UnLocode();
        assertNotNull(unLocode);
    }

    @Test
    void getIdString_returnsUpperCaseCode() {
        UnLocode unLocode = new UnLocode("CNHKG");
        assertEquals("CNHKG", unLocode.getIdString());
    }

    @Test
    void equals_sameObject_returnsTrue() {
        UnLocode unLocode = new UnLocode("USNYC");
        assertEquals(unLocode, unLocode);
    }

    @Test
    void equals_equalCodes_returnsTrue() {
        UnLocode code1 = new UnLocode("USNYC");
        UnLocode code2 = new UnLocode("USNYC");
        assertEquals(code1, code2);
    }

    @Test
    void equals_differentCodes_returnsFalse() {
        UnLocode code1 = new UnLocode("USNYC");
        UnLocode code2 = new UnLocode("CNHKG");
        assertNotEquals(code1, code2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        UnLocode unLocode = new UnLocode("USNYC");
        assertNotEquals(unLocode, null);
    }

    @Test
    void equals_differentType_returnsFalse() {
        UnLocode unLocode = new UnLocode("USNYC");
        assertNotEquals(unLocode, "USNYC");
    }

    @Test
    void hashCode_equalCodes_sameHashCode() {
        UnLocode code1 = new UnLocode("USNYC");
        UnLocode code2 = new UnLocode("USNYC");
        assertEquals(code1.hashCode(), code2.hashCode());
    }

    @Test
    void toString_returnsIdString() {
        UnLocode unLocode = new UnLocode("USNYC");
        assertEquals("USNYC", unLocode.toString());
    }

    @Test
    void sameValueAs_equalCodes_returnsTrue() {
        UnLocode code1 = new UnLocode("USNYC");
        UnLocode code2 = new UnLocode("USNYC");
        assertTrue(code1.sameValueAs(code2));
    }

    @Test
    void sameValueAs_differentCodes_returnsFalse() {
        UnLocode code1 = new UnLocode("USNYC");
        UnLocode code2 = new UnLocode("CNHKG");
        assertFalse(code1.sameValueAs(code2));
    }

    @Test
    void sameValueAs_nullOther_returnsFalse() {
        UnLocode code1 = new UnLocode("USNYC");
        assertFalse(code1.sameValueAs(null));
    }
}
