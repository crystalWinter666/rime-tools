package org.rimecraft.rimetools.module.teleport.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NameValidator {
    private static final Pattern ASCII_ALLOWED = Pattern.compile("^[A-Za-z0-9_-]+$");

    private NameValidator() { }

    public static String normalize(String name) {
        return name == null ? "" : name.trim();
    }

    public static boolean isValid(String name, int maxLength, boolean allowUnicode) {
        String normalized = normalize(name);
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            return false;
        }
        if (allowUnicode) {
            return normalized.codePoints().noneMatch(Character::isWhitespace);
        }
        return ASCII_ALLOWED.matcher(normalized).matches();
    }

    public static String key(String name) {
        return normalize(name).toLowerCase(Locale.ROOT);
    }
}
