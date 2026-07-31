package org.rimecraft.rimetools.module.title.network;

import org.rimecraft.rimetools.module.title.title.TitleInputValidator;

import java.util.regex.Pattern;
import java.util.UUID;

public final class PayloadValidation {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private PayloadValidation() {
    }

    public static boolean isValidPlayerTarget(String value) {
        if (value == null) {
            return false;
        }
        if (PLAYER_NAME.matcher(value).matches()) {
            return true;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean isValidTitleInput(String id, String displayName, String color, int weight) {
        return TitleInputValidator.isValidId(id)
                && TitleInputValidator.isValidDisplayName(displayName)
                && TitleInputValidator.isValidColor(color)
                && weight >= -100_000 && weight <= 100_000;
    }
}
