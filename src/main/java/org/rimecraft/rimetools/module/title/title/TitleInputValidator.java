package org.rimecraft.rimetools.module.title.title;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TitleInputValidator {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final Pattern COLOR = Pattern.compile("#?[0-9a-fA-F]{6}");
    private static final int MAX_DISPLAY_NAME_LENGTH = 32;

    private TitleInputValidator() {
    }

    public static boolean isValidId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    public static boolean isValidDisplayName(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_DISPLAY_NAME_LENGTH
                && value.chars().noneMatch(Character::isISOControl);
    }

    public static boolean isValidColor(String value) {
        return value != null && COLOR.matcher(value).matches();
    }

    public static Optional<String> normalizeColor(String value) {
        if (!isValidColor(value)) {
            return Optional.empty();
        }
        return Optional.of((value.startsWith("#") ? value : "#" + value).toUpperCase(Locale.ROOT));
    }
}