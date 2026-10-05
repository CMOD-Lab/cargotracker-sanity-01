package org.eclipse.cargotracker.domain.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainObjectUtilsTest {

    @Test
    void nullSafe_withNonNullActual_returnsActual() {
        String actual = "actual";
        String safe = "safe";
        assertEquals("actual", DomainObjectUtils.nullSafe(actual, safe));
    }

    @Test
    void nullSafe_withNullActual_returnsSafe() {
        String actual = null;
        String safe = "safe";
        assertEquals("safe", DomainObjectUtils.nullSafe(actual, safe));
    }

    @Test
    void nullSafe_withNullActualAndNullSafe_returnsNull() {
        String actual = null;
        String safe = null;
        assertNull(DomainObjectUtils.nullSafe(actual, safe));
    }

    @Test
    void nullSafe_withObjectType_returnsCorrectValue() {
        Integer actual = 42;
        Integer safe = 0;
        assertEquals(42, DomainObjectUtils.nullSafe(actual, safe));
    }

    @Test
    void nullSafe_withNullObjectType_returnsSafe() {
        Integer actual = null;
        Integer safe = 0;
        assertEquals(0, DomainObjectUtils.nullSafe(actual, safe));
    }
}
