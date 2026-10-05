package org.eclipse.cargotracker.application.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DateConverterTest {

    @Test
    void toDate_withValidDateString_returnsLocalDate() {
        LocalDate date = DateConverter.toDate("1/15/2024");
        assertNotNull(date);
        assertEquals(2024, date.getYear());
        assertEquals(1, date.getMonthValue());
        assertEquals(15, date.getDayOfMonth());
    }

    @Test
    void toDate_withSingleDigitMonth_returnsLocalDate() {
        LocalDate date = DateConverter.toDate("3/5/2024");
        assertNotNull(date);
        assertEquals(3, date.getMonthValue());
        assertEquals(5, date.getDayOfMonth());
    }

    @Test
    void toDateTime_withValidDateTimeString_returnsLocalDateTime() {
        LocalDateTime dateTime = DateConverter.toDateTime("1/15/2024 10:30 AM");
        assertNotNull(dateTime);
        assertEquals(2024, dateTime.getYear());
        assertEquals(1, dateTime.getMonthValue());
        assertEquals(15, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHour());
        assertEquals(30, dateTime.getMinute());
    }

    @Test
    void toDateTime_withPmTime_returnsCorrectDateTime() {
        LocalDateTime dateTime = DateConverter.toDateTime("6/15/2024 2:30 PM");
        assertNotNull(dateTime);
        assertEquals(14, dateTime.getHour());
        assertEquals(30, dateTime.getMinute());
    }

    @Test
    void toString_localDateTime_returnsFormattedString() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        String result = DateConverter.toString(dateTime);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void toString_localDate_returnsFormattedString() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        String result = DateConverter.toString(date);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("1"));
        assertTrue(result.contains("15"));
        assertTrue(result.contains("2024"));
    }

    @Test
    void dateFormat_isCorrect() {
        assertEquals("M/d/yyyy", DateConverter.DATE_FORMAT);
    }

    @Test
    void dateTimeFormat_isCorrect() {
        assertEquals("M/d/yyyy h:m a", DateConverter.DATE_TIME_FORMAT);
    }

    @Test
    void roundTrip_date_preservesValue() {
        LocalDate original = LocalDate.of(2024, 6, 15);
        String formatted = DateConverter.toString(original);
        LocalDate parsed = DateConverter.toDate(formatted);
        assertEquals(original, parsed);
    }

    @Test
    void roundTrip_dateTime_preservesValue() {
        LocalDateTime original = LocalDateTime.of(2024, 6, 15, 14, 30, 0);
        String formatted = DateConverter.toString(original);
        LocalDateTime parsed = DateConverter.toDateTime(formatted);
        assertEquals(original.getYear(), parsed.getYear());
        assertEquals(original.getMonthValue(), parsed.getMonthValue());
        assertEquals(original.getDayOfMonth(), parsed.getDayOfMonth());
        assertEquals(original.getHour(), parsed.getHour());
        assertEquals(original.getMinute(), parsed.getMinute());
    }
}
