package org.rimecraft.rimetools.module.punishment;

/** Compact human-readable duration formatting used in moderation feedback. */
public final class DurationFormatter {
    private DurationFormatter() { }

    public static String format(long seconds) {
        if (seconds < 0) return "permanent";
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        long remaining = seconds % 60;
        StringBuilder result = new StringBuilder();
        append(result, days, "d");
        append(result, hours, "h");
        append(result, minutes, "m");
        if (result.isEmpty() || remaining > 0) append(result, remaining, "s");
        return result.toString();
    }

    private static void append(StringBuilder result, long value, String suffix) {
        if (value <= 0) return;
        if (!result.isEmpty()) result.append(' ');
        result.append(value).append(suffix);
    }
}
