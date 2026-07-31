package org.rimecraft.rimetools.module.punishment.data;

import java.util.UUID;

/** A single punishment action recorded against a player. */
public record PunishmentRecord(
        UUID playerId,
        String playerName,
        Type type,
        long issuedAt,
        long expiresAt,
        String reason,
        String executor
) {
    public enum Type {
        TEMP_BAN,
        PERMA_BAN,
        MUTE,
        KICK,
        CLEAR_RANK
    }

    /** True when the record currently restricts the player (ban or mute, not expired). */
    public boolean isActive(long now) {
        return switch (type) {
            case PERMA_BAN -> true;
            case TEMP_BAN, MUTE -> expiresAt > now;
            case KICK, CLEAR_RANK -> false;
        };
    }
}
