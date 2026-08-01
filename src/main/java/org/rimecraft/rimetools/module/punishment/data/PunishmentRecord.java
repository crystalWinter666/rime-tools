package org.rimecraft.rimetools.module.punishment.data;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A single immutable moderation action recorded against a player. */
public record PunishmentRecord(
        UUID id,
        UUID playerId,
        String playerName,
        Type type,
        long issuedAt,
        long expiresAt,
        String reason,
        String executor,
        long revokedAt,
        String revokedBy,
        String revokeReason
) {
    /** Compatibility constructor used by version-1 records and callers. */
    public PunishmentRecord(UUID playerId, String playerName, Type type, long issuedAt, long expiresAt,
                            String reason, String executor) {
        this(stableLegacyId(playerId, type, issuedAt, expiresAt, reason, executor), playerId, playerName,
                type, issuedAt, expiresAt, reason, executor, 0, null, null);
    }

    public PunishmentRecord {
        if (id == null) id = UUID.randomUUID();
        if (playerId == null) throw new IllegalArgumentException("playerId must not be null");
        if (playerName == null || playerName.isBlank()) playerName = playerId.toString();
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (executor == null || executor.isBlank()) executor = "Console";
    }

    public enum Type {
        WARN,
        TEMP_BAN,
        PERMA_BAN,
        MUTE,
        PERMA_MUTE,
        KICK,
        CLEAR_RANK
    }

    public enum Status {ACTIVE, EXPIRED, REVOKED, RECORDED}

    public boolean revocable() {
        return type == Type.TEMP_BAN || type == Type.PERMA_BAN
                || type == Type.MUTE || type == Type.PERMA_MUTE;
    }

    /** True when this record currently restricts the player. */
    public boolean isActive(long now) {
        if (!revocable() || revokedAt > 0) return false;
        return switch (type) {
            case PERMA_BAN, PERMA_MUTE -> true;
            case TEMP_BAN, MUTE -> expiresAt > now;
            default -> false;
        };
    }

    public Status status(long now) {
        if (revokedAt > 0) return Status.REVOKED;
        if (isActive(now)) return Status.ACTIVE;
        if (revocable()) return Status.EXPIRED;
        return Status.RECORDED;
    }

    public PunishmentRecord revoke(long at, String by, String reason) {
        if (!isActive(at)) return this;
        return new PunishmentRecord(id, playerId, playerName, type, issuedAt, expiresAt,
                this.reason, executor, at, by, reason);
    }

    private static UUID stableLegacyId(UUID playerId, Type type, long issuedAt, long expiresAt,
                                       String reason, String executor) {
        String seed = playerId + "|" + type + "|" + issuedAt + "|" + expiresAt + "|"
                + String.valueOf(reason) + "|" + String.valueOf(executor);
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
