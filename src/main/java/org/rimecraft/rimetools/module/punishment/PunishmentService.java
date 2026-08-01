package org.rimecraft.rimetools.module.punishment;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Central moderation service used by commands, networking and automatic chat actions. */
public final class PunishmentService {
    private final PunishmentModule module;

    public PunishmentService(PunishmentModule module) {
        this.module = module;
    }

    public PunishmentRecord apply(GameProfile target, PunishmentRecord.Type type, long durationSeconds,
                                  String reason, String executor) {
        long now = now();
        long expiresAt = switch (type) {
            case TEMP_BAN, MUTE -> Math.addExact(now, durationSeconds);
            default -> 0;
        };
        PunishmentRecord record = new PunishmentRecord(UUID.randomUUID(), target.id(), target.name(), type,
                now, expiresAt, cleanReason(reason), cleanExecutor(executor), 0, null, null);
        repository().add(record);
        repository().saveIfDirty();
        applyOnlineEffect(record, durationSeconds);
        module.announce(record);
        return record;
    }

    public PunishmentRecord warn(GameProfile target, String reason, String executor) {
        return apply(target, PunishmentRecord.Type.WARN, 0, reason, executor);
    }

    public PunishmentRecord kick(GameProfile target, String reason, String executor) {
        return apply(target, PunishmentRecord.Type.KICK, 0, reason, executor);
    }

    public PunishmentRecord clearRank(GameProfile target, String period, String executor) {
        MinecraftServer server = module.server();
        if (server == null || !PunishmentClearRankService.clearRank(server, target.id(), period)) return null;
        return apply(target, PunishmentRecord.Type.CLEAR_RANK, 0, "clearrank " + period, executor);
    }

    public int revokeActive(UUID playerId, boolean ban, String executor, String reason) {
        long now = now();
        int count = 0;
        if (ban) {
            count += repository().revoke(playerId, PunishmentRecord.Type.PERMA_BAN, now, executor, reason);
            count += repository().revoke(playerId, PunishmentRecord.Type.TEMP_BAN, now, executor, reason);
        } else {
            count += repository().revoke(playerId, PunishmentRecord.Type.PERMA_MUTE, now, executor, reason);
            count += repository().revoke(playerId, PunishmentRecord.Type.MUTE, now, executor, reason);
        }
        repository().saveIfDirty();
        return count;
    }

    public boolean revokeRecord(UUID recordId, String executor, String reason) {
        boolean changed = repository().revoke(recordId, now(), executor, reason);
        repository().saveIfDirty();
        return changed;
    }

    public Optional<PunishmentRecord> activeBan(UUID playerId) {
        return repository().activeBan(playerId, now());
    }

    public Optional<PunishmentRecord> activeMute(UUID playerId) {
        return repository().activeMute(playerId, now());
    }

    public long remaining(PunishmentRecord record) {
        return record.expiresAt() == 0 ? -1 : Math.max(0, record.expiresAt() - now());
    }

    private void applyOnlineEffect(PunishmentRecord record, long durationSeconds) {
        MinecraftServer server = module.server();
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(record.playerId());
        if (online == null) return;
        switch (record.type()) {
            case PERMA_BAN -> online.connection.disconnect(PunishmentText.banned(record));
            case TEMP_BAN -> online.connection.disconnect(PunishmentText.tempBanned(record, durationSeconds));
            case MUTE, PERMA_MUTE -> module.notifyMuted(online, record,
                    record.expiresAt() == 0 ? -1 : durationSeconds);
            case KICK -> online.connection.disconnect(PunishmentText.kicked(record));
            case WARN -> online.sendSystemMessage(PunishmentText.warned(record));
            default -> { }
        }
    }

    private PunishmentRepository repository() {
        PunishmentRepository repository = module.repository();
        if (repository == null) throw new IllegalStateException("Punishment repository is not ready");
        return repository;
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String trimmed = reason.trim();
        return trimmed.length() > 256 ? trimmed.substring(0, 256) : trimmed;
    }

    private static String cleanExecutor(String executor) {
        return executor == null || executor.isBlank() ? "Console" : executor;
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
