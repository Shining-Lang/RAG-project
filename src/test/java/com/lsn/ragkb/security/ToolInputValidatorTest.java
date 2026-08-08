package com.lsn.ragkb.security;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolInputValidatorTest {

    private final ToolInputValidator validator = new ToolInputValidator();

    @Test
    void parsesStrictDateFormat() {
        assertEquals(LocalDate.of(2026, 8, 8), validator.parseDate("2026-08-08"));
        assertThrows(IllegalArgumentException.class, () -> validator.parseDate("2026/08/08"));
        assertThrows(IllegalArgumentException.class, () -> validator.parseDate("2026-02-30"));
    }

    @Test
    void normalizesToolBounds() {
        assertEquals(1, validator.normalizeTopN(0));
        assertEquals(20, validator.normalizeTopN(200));
        assertEquals(-20, validator.normalizeSignedTopN(-200));
        assertEquals(50, validator.normalizeLimit(999));
        assertEquals(24, validator.normalizeMonths(999));
    }

    @Test
    void validatesRangeAndChartDimension() {
        validator.validateDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validateDateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)));
        assertEquals("region", validator.validateChartDimension("region"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateChartDimension("bad"));
    }
}
