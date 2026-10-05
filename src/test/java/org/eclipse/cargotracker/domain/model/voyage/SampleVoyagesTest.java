package org.eclipse.cargotracker.domain.model.voyage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SampleVoyagesTest {

    @Test
    void cm001_isNotNull() {
        assertNotNull(SampleVoyages.CM001);
    }

    @Test
    void cm001_hasCorrectVoyageNumber() {
        assertEquals("CM001", SampleVoyages.CM001.getVoyageNumber().getIdString());
    }

    @Test
    void cm002_isNotNull() {
        assertNotNull(SampleVoyages.CM002);
    }

    @Test
    void cm003_isNotNull() {
        assertNotNull(SampleVoyages.CM003);
    }

    @Test
    void cm004_isNotNull() {
        assertNotNull(SampleVoyages.CM004);
    }

    @Test
    void cm005_isNotNull() {
        assertNotNull(SampleVoyages.CM005);
    }

    @Test
    void cm006_isNotNull() {
        assertNotNull(SampleVoyages.CM006);
    }

    @Test
    void v100_isNotNull() {
        assertNotNull(SampleVoyages.v100);
    }

    @Test
    void v100_hasCorrectVoyageNumber() {
        assertEquals("V100", SampleVoyages.v100.getVoyageNumber().getIdString());
    }

    @Test
    void v200_isNotNull() {
        assertNotNull(SampleVoyages.v200);
    }

    @Test
    void v300_isNotNull() {
        assertNotNull(SampleVoyages.v300);
    }

    @Test
    void v400_isNotNull() {
        assertNotNull(SampleVoyages.v400);
    }

    @Test
    void hongkongToNewYork_isNotNull() {
        assertNotNull(SampleVoyages.HONGKONG_TO_NEW_YORK);
    }

    @Test
    void newYorkToDallas_isNotNull() {
        assertNotNull(SampleVoyages.NEW_YORK_TO_DALLAS);
    }

    @Test
    void dallasTohHelsinki_isNotNull() {
        assertNotNull(SampleVoyages.DALLAS_TO_HELSINKI);
    }

    @Test
    void dallasToHelsinkiAlt_isNotNull() {
        assertNotNull(SampleVoyages.DALLAS_TO_HELSINKI_ALT);
    }

    @Test
    void helsinkiToHongkong_isNotNull() {
        assertNotNull(SampleVoyages.HELSINKI_TO_HONGKONG);
    }

    @Test
    void all_mapIsNotEmpty() {
        assertFalse(SampleVoyages.ALL.isEmpty());
    }

    @Test
    void getAll_returnsNonEmptyList() {
        assertFalse(SampleVoyages.getAll().isEmpty());
    }

    @Test
    void lookup_existingVoyageNumber_returnsVoyage() {
        VoyageNumber voyageNumber = new VoyageNumber("CM001");
        assertNotNull(SampleVoyages.lookup(voyageNumber));
    }

    @Test
    void lookup_nonExistingVoyageNumber_returnsNull() {
        VoyageNumber voyageNumber = new VoyageNumber("XXXXX");
        assertNull(SampleVoyages.lookup(voyageNumber));
    }

    @Test
    void allVoyages_areDistinct() {
        assertNotEquals(SampleVoyages.CM001, SampleVoyages.CM002);
        assertNotEquals(SampleVoyages.v100, SampleVoyages.v200);
    }
}
