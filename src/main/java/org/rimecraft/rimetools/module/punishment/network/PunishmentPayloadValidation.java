package org.rimecraft.rimetools.module.punishment.network;

import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;

import java.util.Locale;
import java.util.UUID;

/** Pure validation helpers for untrusted moderation GUI input. */
public final class PunishmentPayloadValidation {
    private static final long MAX_DURATION_SECONDS = 315_576_000L; // ten years

    private PunishmentPayloadValidation() { }

    public static boolean validRequest(PunishmentPayloads.Request request) {
        return request != null && request.version() == PunishmentPayloads.PROTOCOL_VERSION
                && request.query() != null && request.query().length() <= 64
                && request.page() >= 1 && request.page() <= 1_000_000;
    }

    public static boolean validAction(PunishmentPayloads.Action action) {
        if (action == null || action.action() == null || action.reason() == null
                || action.playerName() == null || action.playerUuid() == null || action.recordId() == null
                || action.reason().length() > 256 || action.playerName().length() > 36) return false;
        String name = action.action().toUpperCase(Locale.ROOT);
        if (name.equals("REVOKE")) return uuid(action.recordId());
        if (!uuid(action.playerUuid())) return false;
        try {
            PunishmentRecord.Type type = PunishmentRecord.Type.valueOf(name);
            if (type == PunishmentRecord.Type.CLEAR_RANK) return false;
            boolean timed = type == PunishmentRecord.Type.TEMP_BAN || type == PunishmentRecord.Type.MUTE;
            return timed ? action.duration() > 0 && action.duration() <= MAX_DURATION_SECONDS
                    : action.duration() == 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean uuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
