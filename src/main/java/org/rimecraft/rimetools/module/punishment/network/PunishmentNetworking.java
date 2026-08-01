package org.rimecraft.rimetools.module.punishment.network;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.punishment.PunishmentModule;
import org.rimecraft.rimetools.module.punishment.PunishmentPermissions;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative administration networking. */
public final class PunishmentNetworking {
    private PunishmentNetworking() { }

    public static void register(PunishmentModule module) {
        PayloadTypeRegistry.serverboundPlay().register(PunishmentPayloads.Request.TYPE, PunishmentPayloads.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PunishmentPayloads.Action.TYPE, PunishmentPayloads.Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PunishmentPayloads.Response.TYPE, PunishmentPayloads.Response.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PunishmentPayloads.Result.TYPE, PunishmentPayloads.Result.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PunishmentPayloads.Request.TYPE,
                (payload, context) -> request(module, context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PunishmentPayloads.Action.TYPE,
                (payload, context) -> action(module, context.player(), payload));
    }

    private static void request(PunishmentModule module, ServerPlayer viewer, PunishmentPayloads.Request request) {
        if (!PunishmentPayloadValidation.validRequest(request)) {
            result(viewer, false, request.version() != PunishmentPayloads.PROTOCOL_VERSION
                    ? "Protocol version mismatch" : "Invalid moderation request");
            return;
        }
        if (!PunishmentPermissions.has(viewer, PunishmentPermissions.HISTORY) || module.repository() == null) {
            result(viewer, false, "You do not have permission to view moderation records");
            return;
        }
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        long now = Instant.now().getEpochSecond();
        Map<UUID, MutablePlayer> players = new LinkedHashMap<>();
        module.repository().allHistory().forEach(record -> players.computeIfAbsent(record.playerId(), ignored ->
                new MutablePlayer(record.playerId(), record.playerName())).accept(record, now));
        if (module.server() != null) module.server().getPlayerList().getPlayers().forEach(player -> {
            MutablePlayer entry = players.computeIfAbsent(player.getUUID(), ignored ->
                    new MutablePlayer(player.getUUID(), player.getGameProfile().name()));
            entry.online = true;
            entry.name = player.getGameProfile().name();
        });
        List<PunishmentPayloads.PlayerEntry> entries = players.values().stream()
                .filter(entry -> query.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(query)
                        || entry.id.toString().contains(query))
                .sorted(Comparator.comparing((MutablePlayer entry) -> entry.online).reversed()
                        .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER))
                .limit(512).map(MutablePlayer::payload).toList();

        List<PunishmentRecord> filtered = module.repository().allHistory().stream()
                .filter(record -> query.isEmpty() || record.playerName().toLowerCase(Locale.ROOT).contains(query)
                        || record.playerId().toString().contains(query) || record.type().name().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        int pageSize = Math.min(PunishmentPayloads.MAX_PAGE_SIZE, module.config().historyPageSize());
        int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        int page = Math.clamp(request.page(), 1, pages);
        List<PunishmentPayloads.RecordEntry> records = filtered.stream().skip((long) (page - 1) * pageSize)
                .limit(pageSize).map(record -> record(record, now)).toList();
        ServerPlayNetworking.send(viewer, new PunishmentPayloads.Response(PunishmentPayloads.PROTOCOL_VERSION,
                entries, records, page, pages,
                PunishmentPermissions.has(viewer, PunishmentPermissions.APPLY),
                PunishmentPermissions.has(viewer, PunishmentPermissions.REVOKE)));
    }

    private static void action(PunishmentModule module, ServerPlayer executor, PunishmentPayloads.Action payload) {
        if (!PunishmentPayloadValidation.validAction(payload)) {
            result(executor, false, "Invalid moderation action");
            return;
        }
        try {
            String action = payload.action().toUpperCase(Locale.ROOT);
            if (action.equals("REVOKE")) {
                if (!PunishmentPermissions.has(executor, PunishmentPermissions.REVOKE)) throw new SecurityException();
                UUID recordId = UUID.fromString(payload.recordId());
                if (!module.service().revokeRecord(recordId, executor.getGameProfile().name(), blank(payload.reason()))) {
                    result(executor, false, "The record is missing or no longer active");
                    return;
                }
            } else {
                if (!PunishmentPermissions.has(executor, PunishmentPermissions.APPLY)) throw new SecurityException();
                UUID playerId = UUID.fromString(payload.playerUuid());
                String name = payload.playerName().isBlank() ? playerId.toString() : payload.playerName();
                GameProfile target = new GameProfile(playerId, name);
                PunishmentRecord.Type type = PunishmentRecord.Type.valueOf(action);
                if (type == PunishmentRecord.Type.CLEAR_RANK) throw new IllegalArgumentException();
                module.service().apply(target, type, payload.duration(), blank(payload.reason()),
                        executor.getGameProfile().name());
            }
            result(executor, true, "Moderation action completed");
        } catch (SecurityException exception) {
            result(executor, false, "You do not have permission for this action");
        } catch (RuntimeException exception) {
            result(executor, false, "Invalid moderation action");
        }
    }

    private static PunishmentPayloads.RecordEntry record(PunishmentRecord record, long now) {
        return new PunishmentPayloads.RecordEntry(record.id().toString(), record.playerId().toString(),
                record.playerName(), record.type().name(), record.status(now).name(), record.issuedAt(),
                record.expiresAt(), record.reason() == null ? "" : record.reason(), record.executor());
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void result(ServerPlayer player, boolean success, String message) {
        ServerPlayNetworking.send(player, new PunishmentPayloads.Result(success, message));
    }

    private static final class MutablePlayer {
        private final UUID id;
        private String name;
        private boolean online;
        private int warnings;
        private boolean banned;
        private boolean muted;
        private MutablePlayer(UUID id, String name) { this.id = id; this.name = name; }
        private void accept(PunishmentRecord record, long now) {
            if (record.type() == PunishmentRecord.Type.WARN) warnings++;
            if (record.isActive(now)) {
                banned |= record.type() == PunishmentRecord.Type.TEMP_BAN || record.type() == PunishmentRecord.Type.PERMA_BAN;
                muted |= record.type() == PunishmentRecord.Type.MUTE || record.type() == PunishmentRecord.Type.PERMA_MUTE;
            }
        }
        private PunishmentPayloads.PlayerEntry payload() {
            return new PunishmentPayloads.PlayerEntry(id.toString(), name, online, warnings, banned, muted);
        }
    }
}
