package org.rimecraft.rimetools.module.punishment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationFormatterTest {
    @Test void formatsCompactDurations() {
        assertEquals("5s", DurationFormatter.format(5));
        assertEquals("1m 5s", DurationFormatter.format(65));
        assertEquals("1d 2h 3m 4s", DurationFormatter.format(93_784));
        assertEquals("permanent", DurationFormatter.format(-1));
    }
}
