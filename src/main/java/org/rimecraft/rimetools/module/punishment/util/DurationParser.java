package org.rimecraft.rimetools.module.punishment.util;

import java.util.Locale;

/** Parses duration strings like {@code 30m}, {@code 2h}, {@code 7d} or plain seconds. */
public final class DurationParser {
    private DurationParser() {
    }

    /** Returns seconds, or -1 when the input is blank/invalid/non-positive. */
    public static long parseSeconds(String input) {
        if (input == null) return -1;
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return -1;

        long multiplier = 1;
        char suffix = value.charAt(value.length() - 1);
        switch (suffix) {
            case 's' -> {
                multiplier = 1;
                value = value.substring(0, value.length() - 1);
            }
            case 'm' -> {
                multiplier = 60;
                value = value.substring(0, value.length() - 1);
            }
            case 'h' -> {
                multiplier = 3600;
                value = value.substring(0, value.length() - 1);
            }
            case 'd' -> {
                multiplier = 86400;
                value = value.substring(0, value.length() - 1);
            }
            default -> {
                if (suffix < '0' || suffix > '9') return -1;
            }
        }

        try {
            long number = Long.parseLong(value);
            if (number <= 0 || number > Long.MAX_VALUE / multiplier) return -1;
            return number * multiplier;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
