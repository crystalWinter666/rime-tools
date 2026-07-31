package org.rimecraft.rimetools.module.teleport.util;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static String formatSeconds(long seconds) {
        long safe = Math.max(0, seconds);
        long minutes = safe / 60;
        long remainder = safe % 60;
        return minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }
}
