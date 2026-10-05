package org.eclipse.cargotracker.domain.model.location;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SampleLocationsTest {

    @Test
    void hongkong_isNotNull() {
        assertNotNull(SampleLocations.HONGKONG);
    }

    @Test
    void hongkong_hasCorrectCode() {
        assertEquals("CNHKG", SampleLocations.HONGKONG.getUnLocode().getIdString());
    }

    @Test
    void hongkong_hasCorrectName() {
        assertEquals("Hong Kong", SampleLocations.HONGKONG.getName());
    }

    @Test
    void melbourne_isNotNull() {
        assertNotNull(SampleLocations.MELBOURNE);
    }

    @Test
    void stockholm_isNotNull() {
        assertNotNull(SampleLocations.STOCKHOLM);
    }

    @Test
    void stockholm_hasCorrectCode() {
        assertEquals("SESTO", SampleLocations.STOCKHOLM.getUnLocode().getIdString());
    }

    @Test
    void helsinki_isNotNull() {
        assertNotNull(SampleLocations.HELSINKI);
    }

    @Test
    void chicago_isNotNull() {
        assertNotNull(SampleLocations.CHICAGO);
    }

    @Test
    void tokyo_isNotNull() {
        assertNotNull(SampleLocations.TOKYO);
    }

    @Test
    void hamburg_isNotNull() {
        assertNotNull(SampleLocations.HAMBURG);
    }

    @Test
    void hamburg_hasCorrectCode() {
        assertEquals("DEHAM", SampleLocations.HAMBURG.getUnLocode().getIdString());
    }

    @Test
    void shanghai_isNotNull() {
        assertNotNull(SampleLocations.SHANGHAI);
    }

    @Test
    void rotterdam_isNotNull() {
        assertNotNull(SampleLocations.ROTTERDAM);
    }

    @Test
    void gothenburg_isNotNull() {
        assertNotNull(SampleLocations.GOTHENBURG);
    }

    @Test
    void hangzou_isNotNull() {
        assertNotNull(SampleLocations.HANGZOU);
    }

    @Test
    void newyork_isNotNull() {
        assertNotNull(SampleLocations.NEWYORK);
    }

    @Test
    void newyork_hasCorrectCode() {
        assertEquals("USNYC", SampleLocations.NEWYORK.getUnLocode().getIdString());
    }

    @Test
    void dallas_isNotNull() {
        assertNotNull(SampleLocations.DALLAS);
    }

    @Test
    void allLocations_areDistinct() {
        assertNotEquals(SampleLocations.HONGKONG, SampleLocations.STOCKHOLM);
        assertNotEquals(SampleLocations.HAMBURG, SampleLocations.TOKYO);
        assertNotEquals(SampleLocations.NEWYORK, SampleLocations.DALLAS);
    }
}
