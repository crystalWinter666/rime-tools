package org.rimecraft.rimetools.module.teleport.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.teleport.TeleportModule;
import org.rimecraft.rimetools.module.teleport.i18n.MessageService;
import org.rimecraft.rimetools.module.teleport.integration.CarpetPermBridge;
import org.rimecraft.rimetools.module.teleport.model.FakePlayerInfo;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.rimecraft.rimetools.module.teleport.model.Waypoint;
import org.rimecraft.rimetools.module.teleport.teleport.TeleportService;
import org.rimecraft.rimetools.module.teleport.teleport.TeleportType;
import org.rimecraft.rimetools.module.teleport.util.NameValidator;
import org.rimecraft.rimetools.module.teleport.util.Permissions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeleportNetworking {

    private TeleportNetworking() { }

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(OpenWaypointScreenPayload.TYPE, OpenWaypointScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TpaToastPayload.TYPE, TpaToastPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TpaResultPayload.TYPE, TpaResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WaypointActionPayload.TYPE, WaypointActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TpaAllowlistActionPayload.TYPE, TpaAllowlistActionPayload.STREAM_CODEC);
    }

    public static void registerServerReceivers(TeleportModule mod) {
        ServerPlayNetworking.registerGlobalReceiver(WaypointActionPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handleAction(mod, context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TpaAllowlistActionPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        handleAllowlistAction(mod, context.player(), payload)));
    }

    private static void handleAllowlistAction(TeleportModule mod, ServerPlayer player,
                                              TpaAllowlistActionPayload payload) {
        UUID targetId = payload.targetUuid();
        if (!Permissions.has(player, "tpa.allowlist", true)) {
            mod.messages().send(player, "no_permission");
            return;
        }
        if (targetId.equals(player.getUUID())) {
            mod.messages().send(player, "tpa.self");
            return;
        }

        boolean alreadyAllowed = mod.allowlist().isAllowed(player.getUUID(), targetId);
        boolean known = mod.server().getPlayerList().getPlayer(targetId) != null
                || mod.offlinePositions().get(targetId) != null;
        if (payload.allowed() && !known && !alreadyAllowed) {
            mod.messages().send(player, "player.not_found",
                    MessageService.vars("player", targetId.toString()));
            return;
        }

        boolean changed = payload.allowed()
                ? mod.allowlist().add(player.getUUID(), targetId)
                : mod.allowlist().remove(player.getUUID(), targetId);
        mod.allowlist().saveIfDirty();
        String key = payload.allowed()
                ? changed ? "tpa.allow.added" : "tpa.allow.exists"
                : changed ? "tpa.allow.removed" : "tpa.allow.not_found";
        mod.messages().send(player, key, MessageService.vars(
                "player", playerName(mod, targetId)));
    }

    private static void handleAction(TeleportModule mod, ServerPlayer player, WaypointActionPayload payload) {
        boolean isAdmin = payload.mode() == WaypointActionPayload.MODE_ADMIN
                && payload.targetUuid() != null && Permissions.has(player, "admin", false);
        UUID ownerUuid = isAdmin
                ? payload.targetUuid() : player.getUUID();

        switch (payload.action()) {
            case WaypointActionPayload.ACTION_TELEPORT -> handleTeleport(mod, player, payload, ownerUuid);
            case WaypointActionPayload.ACTION_DELETE -> handleDelete(mod, player, payload, ownerUuid, isAdmin);
            case WaypointActionPayload.ACTION_EDIT_DESC -> handleEditDetails(mod, player, payload, ownerUuid, isAdmin);
            case WaypointActionPayload.ACTION_CREATE -> handleCreate(mod, player, payload, ownerUuid);
            case WaypointActionPayload.ACTION_REFRESH -> handleRefresh(mod, player, payload);
            case WaypointActionPayload.ACTION_TELEPORT_FAKE -> handleFakePlayerTeleport(mod, player, payload);
        }
    }

    private static void handleTeleport(TeleportModule mod, ServerPlayer player,
                                        WaypointActionPayload payload, UUID ownerUuid) {
        if (payload.waypointName() == null) {
            refreshScreen(mod, player, payload);
            return;
        }
        boolean personal = payload.scope() == WaypointActionPayload.SCOPE_PERSONAL;
        if (!Permissions.has(player, personal ? "personal.tp" : "global.tp", true)) {
            mod.messages().send(player, "no_permission");
            return;
        }
        Waypoint waypoint = personal
                ? mod.waypoints().getPersonal(ownerUuid, payload.waypointName())
                : mod.waypoints().getGlobal(payload.waypointName());
        if (waypoint == null) return;
        TeleportService.Result result = mod.teleports().teleport(player, waypoint.position(),
                personal ? TeleportType.WAYPOINT_PERSONAL : TeleportType.WAYPOINT_GLOBAL, false);
        if (result == TeleportService.Result.SUCCESS) {
            mod.messages().send(player, "teleport.success_waypoint",
                    MessageService.vars("name", waypoint.getName()));
        }
    }

    private static void handleDelete(TeleportModule mod, ServerPlayer player,
                                      WaypointActionPayload payload, UUID ownerUuid, boolean isAdmin) {
        if (payload.waypointName() == null) {
            refreshScreen(mod, player, payload);
            return;
        }
        boolean personal = payload.scope() == WaypointActionPayload.SCOPE_PERSONAL;
        boolean removed;
        if (personal) {
            if (isAdmin) {
                removed = mod.waypoints().deletePersonal(ownerUuid, payload.waypointName());
            } else {
                removed = mod.waypoints().deletePersonal(player.getUUID(), payload.waypointName());
            }
        } else {
            if (isAdmin) {
                removed = mod.waypoints().deleteGlobal(payload.waypointName());
            } else {
                removed = mod.waypoints().deleteGlobalOwner(player.getUUID(), payload.waypointName());
            }
        }

        if (removed) {
            mod.messages().send(player, "waypoint.deleted",
                    MessageService.vars("name", payload.waypointName()));
        }
        refreshScreen(mod, player, payload);
    }

    private static void handleEditDetails(TeleportModule mod, ServerPlayer player,
                                          WaypointActionPayload payload, UUID ownerUuid, boolean isAdmin) {
        if (payload.waypointName() == null) {
            refreshScreen(mod, player, payload);
            return;
        }
        boolean personal = payload.scope() == WaypointActionPayload.SCOPE_PERSONAL;
        Waypoint waypoint = personal
                ? mod.waypoints().getPersonal(ownerUuid, payload.waypointName())
                : mod.waypoints().getGlobal(payload.waypointName());
        if (waypoint == null) {
            refreshScreen(mod, player, payload);
            return;
        }
        if (!isAdmin && !personal && waypoint.getOwner() != null
                && !waypoint.getOwner().equals(player.getUUID())) {
            mod.messages().send(player, "waypoint.not_owner",
                    MessageService.vars("name", waypoint.getName()));
            refreshScreen(mod, player, payload);
            return;
        }
        waypoint.setAlias(normalizeOptional(payload.alias(), 48));
        waypoint.setDescription(normalizeOptional(payload.description(), 256));
        waypoint.setUpdatedAt(Instant.now().getEpochSecond());
        if (personal) {
            mod.waypoints().setPersonal(ownerUuid, waypoint);
        } else {
            mod.waypoints().setGlobal(waypoint);
        }
        mod.messages().send(player, "waypoint.details_updated",
                MessageService.vars("name", waypoint.getName()));
        refreshScreen(mod, player, payload);
    }

    private static void handleCreate(TeleportModule mod, ServerPlayer player,
                                      WaypointActionPayload payload, UUID ownerUuid) {
        if (payload.waypointName() == null) {
            refreshScreen(mod, player, payload);
            return;
        }
        String name = NameValidator.normalize(payload.waypointName());
        if (!NameValidator.isValid(name, mod.config().waypointNameMaxLength, mod.config().allowUnicodeNames)) {
            mod.messages().send(player, "waypoint.invalid_name",
                    MessageService.vars("max", mod.config().waypointNameMaxLength));
            refreshScreen(mod, player, payload);
            return;
        }
        boolean personal = payload.scope() == WaypointActionPayload.SCOPE_PERSONAL;
        if (!Permissions.has(player, personal ? "personal" : "global", personal)) {
            mod.messages().send(player, "no_permission");
            refreshScreen(mod, player, payload);
            return;
        }
        Waypoint existing = personal
                ? mod.waypoints().getPersonal(ownerUuid, name)
                : mod.waypoints().getGlobal(name);
        int count = personal ? mod.waypoints().countPersonal(ownerUuid) : mod.waypoints().countGlobal();
        int limit = personal ? mod.config().personalMaxWaypoints : mod.config().globalMaxWaypoints;
        if (existing == null && count >= limit) {
            mod.messages().send(player, "waypoint.limit",
                    MessageService.vars("max", limit));
            refreshScreen(mod, player, payload);
            return;
        }
        if (existing != null && !payload.overwrite()) {
            mod.messages().send(player, "waypoint.exists");
            refreshScreen(mod, player, payload);
            return;
        }

        long now = Instant.now().getEpochSecond();
        TeleportPosition pos = TeleportPosition.from(player);
        Waypoint waypoint = new Waypoint(name, pos.world(), pos.x(), pos.y(), pos.z(),
                pos.yaw(), pos.pitch(), normalizeOptional(payload.alias(), 48),
                normalizeOptional(payload.description(), 256),
                player.getUUID(),
                existing == null ? now : existing.getCreatedAt(), now);
        if (personal) {
            mod.waypoints().setPersonal(ownerUuid, waypoint);
        } else {
            mod.waypoints().setGlobal(waypoint);
        }
        mod.messages().send(player, existing == null ? "waypoint.created" : "waypoint.updated",
                MessageService.vars("name", name));
        refreshScreen(mod, player, payload);
    }

    private static void handleRefresh(TeleportModule mod, ServerPlayer player, WaypointActionPayload payload) {
        refreshScreen(mod, player, payload);
    }

    private static void handleFakePlayerTeleport(TeleportModule mod, ServerPlayer player,
                                                 WaypointActionPayload payload) {
        if (payload.waypointName() == null) return;
        FakePlayerInfo fakePlayer = CarpetPermBridge.findFakePlayer(
                mod.server(), payload.waypointName(), player.getUUID()).orElse(null);
        if (fakePlayer == null) {
            mod.messages().send(player, "fakeplayer.not_found",
                    MessageService.vars("name", payload.waypointName()));
            refreshScreen(mod, player, payload);
            return;
        }
        if (!fakePlayer.ownedByViewer()) {
            mod.messages().send(player, "fakeplayer.not_creator",
                    MessageService.vars("name", fakePlayer.name()));
            refreshScreen(mod, player, payload);
            return;
        }
        TeleportService.Result result = mod.teleports().teleport(
                player, fakePlayer.position(), TeleportType.FAKE_PLAYER, false);
        if (result == TeleportService.Result.SUCCESS) {
            mod.messages().send(player, "fakeplayer.teleport_success",
                    MessageService.vars("name", fakePlayer.name()));
        }
    }

    private static void refreshScreen(TeleportModule mod, ServerPlayer player, WaypointActionPayload payload) {
        boolean isAdmin = payload.mode() == WaypointActionPayload.MODE_ADMIN
                && payload.targetUuid() != null && Permissions.has(player, "admin", false);
        boolean isOtherReadOnly = payload.mode() == WaypointActionPayload.MODE_OTHER_READ_ONLY
                && payload.targetUuid() != null && Permissions.has(player, "other_personal", false);
        String targetName = null;
        if (isAdmin || isOtherReadOnly) {
            ServerPlayer target = mod.server().getPlayerList().getPlayer(payload.targetUuid());
            if (target != null) {
                targetName = target.getName().getString();
            } else {
                var saved = mod.offlinePositions().get(payload.targetUuid());
                targetName = saved != null && saved.playerName() != null
                        ? saved.playerName() : payload.targetUuid().toString();
            }
        }
        int mode = isAdmin ? WaypointActionPayload.MODE_ADMIN
                : isOtherReadOnly ? WaypointActionPayload.MODE_OTHER_READ_ONLY
                : WaypointActionPayload.MODE_OWN;
        sendWaypointScreen(mod, player, mode,
                isAdmin || isOtherReadOnly ? payload.targetUuid() : null, targetName);
    }

    public static void sendWaypointScreen(TeleportModule mod, ServerPlayer player,
                                          int mode, UUID targetUuid, String targetName) {
        boolean isAdmin = mode == WaypointActionPayload.MODE_ADMIN && targetUuid != null
                && Permissions.has(player, "admin", false);
        boolean isOtherReadOnly = mode == WaypointActionPayload.MODE_OTHER_READ_ONLY && targetUuid != null
                && Permissions.has(player, "other_personal", false);
        UUID personalOwner = isAdmin || isOtherReadOnly ? targetUuid : player.getUUID();
        List<Waypoint> personal = mod.waypoints().listPersonal(personalOwner);
        List<Waypoint> global = mod.waypoints().listGlobal();
        List<FakePlayerInfo> fakePlayers = CarpetPermBridge.listFakePlayers(mod.server(), player.getUUID());
        Map<UUID, TeleportPlayerTarget> playerTargets = new LinkedHashMap<>();
        mod.allowlist().list(player.getUUID()).forEach(allowedId -> {
            if (!allowedId.equals(player.getUUID())) {
                playerTargets.put(allowedId, new TeleportPlayerTarget(
                        allowedId, allowedId.toString(), false, true));
            }
        });
        mod.offlinePositions().knownPlayers().forEach(known -> {
            if (!known.id().equals(player.getUUID())) {
                playerTargets.put(known.id(), new TeleportPlayerTarget(known.id(), known.name(), false,
                        mod.allowlist().isAllowed(player.getUUID(), known.id())));
            }
        });
        mod.server().getPlayerList().getPlayers().forEach(online -> {
            if (!online.getUUID().equals(player.getUUID())) {
                playerTargets.put(online.getUUID(), new TeleportPlayerTarget(
                        online.getUUID(), online.getName().getString(), true,
                        mod.allowlist().isAllowed(player.getUUID(), online.getUUID())));
            }
        });
        List<TeleportPlayerTarget> targets = new ArrayList<>(playerTargets.values());
        targets.sort(java.util.Comparator
                .comparing(TeleportPlayerTarget::online).reversed()
                .thenComparing(TeleportPlayerTarget::name, String.CASE_INSENSITIVE_ORDER));
        int acceptedMode = isAdmin ? WaypointActionPayload.MODE_ADMIN
                : isOtherReadOnly ? WaypointActionPayload.MODE_OTHER_READ_ONLY
                : WaypointActionPayload.MODE_OWN;
        ServerPlayNetworking.send(player, new OpenWaypointScreenPayload(
                acceptedMode,
                isAdmin || isOtherReadOnly ? targetUuid : null,
                isAdmin || isOtherReadOnly ? targetName : null,
                personal, global, fakePlayers, targets,
                Permissions.has(player, "tpa", true),
                Permissions.has(player, "tpahere", true),
                Permissions.has(player, "last", false),
                Permissions.has(player, "other_personal", false),
                Permissions.has(player, "tpa.allowlist", true)));
    }

    private static String playerName(TeleportModule mod, UUID playerId) {
        ServerPlayer online = mod.server().getPlayerList().getPlayer(playerId);
        if (online != null) return online.getName().getString();
        var saved = mod.offlinePositions().get(playerId);
        return saved != null && saved.playerName() != null ? saved.playerName() : playerId.toString();
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
