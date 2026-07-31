package org.rimecraft.rimetools.module.punishment.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationParserTest {
    @Test
    void parsesSuffixes() {
        assertEquals(1800, DurationParser.parseSeconds("30m"));
        assertEquals(7200, DurationParser.parseSeconds("2h"));
        assertEquals(604800, DurationParser.parseSeconds("7d"));
        assertEquals(5, DurationParser.parseSeconds("5s"));
        assertEquals(90, DurationParser.parseSeconds("90"));
        assertEquals(1800, DurationParser.parseSeconds(" 30M "));
    }

    @Test
    void rejectsInvalidInput() {
        assertEquals(-1, DurationParser.parseSeconds(null));
        assertEquals(-1, DurationParser.parseSeconds(""));
        assertEquals(-1, DurationParser.parseSeconds("abc"));
        assertEquals(-1, DurationParser.parseSeconds("-5"));
        assertEquals(-1, DurationParser.parseSeconds("0"));
        assertEquals(-1, DurationParser.parseSeconds("1x"));
    }
}
