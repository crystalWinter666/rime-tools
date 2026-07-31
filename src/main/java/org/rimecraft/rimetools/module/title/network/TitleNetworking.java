package org.rimecraft.rimetools.module.title.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.title.TitleModule;
import org.rimecraft.rimetools.module.title.permission.TitlePermissions;
import org.rimecraft.rimetools.module.title.permission.PermissionChecker;
import org.rimecraft.rimetools.module.title.storage.TitleRepository;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;
import org.rimecraft.rimetools.module.title.title.TitleInputValidator;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class TitleNetworking {
    private TitleNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TitlePayloads.RequestTitles.TYPE, TitlePayloads.RequestTitles.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TitlePayloads.SelectTitle.TYPE, TitlePayloads.SelectTitle.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TitlePayloads.UpsertTitle.TYPE, TitlePayloads.UpsertTitle.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TitlePayloads.DeleteTitle.TYPE, TitlePayloads.DeleteTitle.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TitlePayloads.AssignTitle.TYPE, TitlePayloads.AssignTitle.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TitlePayloads.TitlesResponse.TYPE, TitlePayloads.TitlesResponse.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TitlePayloads.OperationResult.TYPE, TitlePayloads.OperationResult.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TitlePayloads.RequestTitles.TYPE, (payload, context) -> {
            if (payload.protocolVersion() != TitlePayloads.PROTOCOL_VERSION) {
                result(context.player(), false, "rime-tools.title.error.protocol");
                return;
            }
            sendTitles(context.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(TitlePayloads.SelectTitle.TYPE, (payload, context) ->
                select(context.player(), payload.titleId()));
        ServerPlayNetworking.registerGlobalReceiver(TitlePayloads.UpsertTitle.TYPE, (payload, context) ->
                upsert(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(TitlePayloads.DeleteTitle.TYPE, (payload, context) ->
                delete(context.player(), payload.titleId()));
        ServerPlayNetworking.registerGlobalReceiver(TitlePayloads.AssignTitle.TYPE, (payload, context) ->
                assign(context.player(), payload));
    }

    public static void sendTitles(ServerPlayer player) {
        TitleRepository repository = TitleModule.repository();
        if (repository == null) {
            result(player, false, "rime-tools.title.error.not_ready");
            return;
        }
        if (!has(player, TitlePermissions.ADMIN_ASSIGN)) {
            ServerPlayNetworking.send(player, response(repository, player, List.of()));
            return;
        }
        var server = player.level().getServer();
        TitleModule.permissionChecker().knownPlayers()
                .completeOnTimeout(List.of(), 3, TimeUnit.SECONDS)
                .exceptionally(ignored -> List.of())
                .thenAccept(known -> server.execute(() -> {
                    if (TitleModule.repository() != null) {
                        ServerPlayNetworking.send(player, response(repository, player,
                                mergePlayerTargets(player, known)));
                    }
                }));
    }

    private static void select(ServerPlayer player, String titleId) {
        TitleRepository repository = TitleModule.repository();
        if (repository == null || !TitleModule.permissionChecker().available()) {
            result(player, false, "rime-tools.title.error.permissions_unavailable");
            return;
        }
        if (!TitleInputValidator.isValidId(titleId)) {
            result(player, false, "rime-tools.title.error.invalid_title");
            return;
        }
        TitleDefinition title = repository.state().title(titleId);
        if (title == null || !title.enabled()
                || !TitleModule.permissionChecker().has(player, TitlePermissions.title(titleId))) {
            result(player, false, "rime-tools.title.error.title_locked");
            return;
        }
        repository.select(player.getUUID(), titleId);
        result(player, true, "rime-tools.title.success.selected");
        sendTitles(player);
    }

    private static void upsert(ServerPlayer player, TitlePayloads.UpsertTitle payload) {
        if (!has(player, TitlePermissions.ADMIN_TITLES)) {
            result(player, false, "rime-tools.title.error.forbidden");
            return;
        }
        try {
            TitleDefinition title = new TitleDefinition(payload.id(), payload.displayName(), payload.color(),
                    payload.weight(), payload.enabled());
            TitleModule.repository().put(title);
            result(player, true, "rime-tools.title.success.saved");
            sendTitles(player);
        } catch (IllegalArgumentException exception) {
            result(player, false, "rime-tools.title.error.invalid_title");
        }
    }

    private static void delete(ServerPlayer player, String titleId) {
        if (!has(player, TitlePermissions.ADMIN_TITLES)) {
            result(player, false, "rime-tools.title.error.forbidden");
            return;
        }
        if (!TitleInputValidator.isValidId(titleId) || !TitleModule.repository().remove(titleId)) {
            result(player, false, "rime-tools.title.error.invalid_title");
            return;
        }
        result(player, true, "rime-tools.title.success.deleted");
        sendTitles(player);
    }

    private static void assign(ServerPlayer player, TitlePayloads.AssignTitle payload) {
        if (!has(player, TitlePermissions.ADMIN_ASSIGN)) {
            result(player, false, "rime-tools.title.error.forbidden");
            return;
        }
        TitleRepository repository = TitleModule.repository();
        if (!PayloadValidation.isValidPlayerTarget(payload.playerTarget())
                || !TitleInputValidator.isValidId(payload.titleId())
                || repository.state().title(payload.titleId()) == null) {
            result(player, false, "rime-tools.title.error.invalid_assignment");
            return;
        }
        TitleModule.permissionChecker().update(payload.playerTarget(), payload.titleId(), payload.granted())
                .whenComplete((success, error) -> player.level().getServer().execute(() -> {
                    result(player, error == null && Boolean.TRUE.equals(success),
                            error == null && Boolean.TRUE.equals(success)
                                    ? "rime-tools.title.success.assignment"
                                    : "rime-tools.title.error.assignment_failed");
                    sendTitles(player);
                }));
    }

    private static boolean has(ServerPlayer player, String permission) {
        return TitleModule.repository() != null
                && TitleModule.permissionChecker().available()
                && (TitleModule.permissionChecker().has(player, TitlePermissions.ADMIN)
                || TitleModule.permissionChecker().has(player, permission));
    }

    private static TitlePayloads.TitlesResponse response(TitleRepository repository, ServerPlayer player,
                                                                 List<TitlePayloads.PlayerTarget> playerTargets) {
        repository.findVisibleTitle(player, TitleModule.permissionChecker());
        String selected = repository.state().selection(player.getUUID());
        boolean permissionsAvailable = TitleModule.permissionChecker().available();
        var titles = repository.state().titles().values().stream()
                .sorted(Comparator.comparingInt(TitleDefinition::weight).reversed()
                        .thenComparing(TitleDefinition::id))
                .map(title -> new TitlePayloads.TitleEntry(
                        new TitlePayloads.TitleMeta(title.id(), title.displayName(), title.color(), title.weight()),
                        title.enabled(),
                        permissionsAvailable && TitleModule.permissionChecker().has(player, TitlePermissions.title(title.id())),
                        title.id().equals(selected)))
                .toList();
        var fallback = repository.fallbackComponent();
        var capabilities = new TitlePayloads.Capabilities(
                permissionsAvailable,
                has(player, TitlePermissions.ADMIN_TITLES),
                has(player, TitlePermissions.ADMIN_ASSIGN));
        return new TitlePayloads.TitlesResponse(TitlePayloads.PROTOCOL_VERSION, titles,
                fallback.getString(), fallback.getStyle().getColor().serialize(), capabilities, playerTargets);
    }

    private static List<TitlePayloads.PlayerTarget> mergePlayerTargets(
            ServerPlayer viewer, List<PermissionChecker.KnownPlayer> knownPlayers) {
        Map<UUID, TitlePayloads.PlayerTarget> targets = new LinkedHashMap<>();
        knownPlayers.forEach(known -> {
            String uuid = known.id().toString();
            String name = known.name() == null || known.name().isBlank() ? uuid : known.name();
            targets.put(known.id(), new TitlePayloads.PlayerTarget(name, uuid, false));
        });
        viewer.level().getServer().getPlayerList().getPlayers().forEach(online -> {
            String uuid = online.getUUID().toString();
            targets.put(online.getUUID(), new TitlePayloads.PlayerTarget(
                    online.getGameProfile().name(), uuid, true));
        });
        return targets.values().stream()
                .sorted(Comparator.comparing(TitlePayloads.PlayerTarget::online).reversed()
                        .thenComparing(TitlePayloads.PlayerTarget::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static void result(ServerPlayer player, boolean success, String messageKey) {
        ServerPlayNetworking.send(player, new TitlePayloads.OperationResult(success, messageKey));
    }
}
