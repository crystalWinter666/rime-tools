package org.rimecraft.rimetools.module.teleport.teleport;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.i18n.MessageService;
import org.rimecraft.rimetools.module.teleport.manager.BackManager;
import org.rimecraft.rimetools.module.teleport.manager.ConfirmManager;
import org.rimecraft.rimetools.module.teleport.manager.CooldownManager;
import org.rimecraft.rimetools.module.teleport.manager.CostManager;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.rimecraft.rimetools.module.teleport.safety.SafetyChecker;
import org.rimecraft.rimetools.module.teleport.util.Permissions;
import org.rimecraft.rimetools.module.teleport.util.TimeUtil;

import java.util.Set;

public final class TeleportService {
    private final MinecraftServer server;
    private final TeleportConfig config;
    private final MessageService messages;
    private final CooldownManager cooldowns;
    private final CostManager costs;
    private final SafetyChecker safety;
    private final ConfirmManager confirms;
    private final BackManager backs;

    public TeleportService(MinecraftServer server, TeleportConfig config, MessageService messages,
                           CooldownManager cooldowns, CostManager costs, SafetyChecker safety,
                           ConfirmManager confirms, BackManager backs) {
        this.server = server;
        this.config = config;
        this.messages = messages;
        this.cooldowns = cooldowns;
        this.costs = costs;
        this.safety = safety;
        this.confirms = confirms;
        this.backs = backs;
    }

    public Result teleport(ServerPlayer player, TeleportPosition requested, TeleportType type, boolean skipSafety) {
        if (player == null || requested == null) return Result.FAILED;
        ServerLevel destinationLevel = requested.resolve(server);
        if (destinationLevel == null) {
            messages.send(player, "world.missing", MessageService.vars("world", requested.world()));
            return Result.FAILED;
        }

        TeleportPosition before = TeleportPosition.from(player);
        if (!config.isWorldAllowed(before.world())) {
            messages.send(player, "world.not_allowed");
            return Result.FAILED;
        }
        if (!config.isWorldAllowed(requested.world())) {
            messages.send(player, "world.not_allowed_target", MessageService.vars("world", requested.world()));
            return Result.FAILED;
        }
        boolean crossworld = !TeleportConfig.normalizeWorld(before.world())
                .equalsIgnoreCase(TeleportConfig.normalizeWorld(requested.world()));
        if (crossworld && !Permissions.has(player, "crossworld", false)) {
            messages.send(player, "world.crossworld_denied", MessageService.vars("world", requested.world()));
            return Result.FAILED;
        }

        boolean cooldownBypass = Permissions.has(player, "cooldown.bypass", false);
        if (!cooldownBypass) {
            long remaining = cooldowns.remaining(player.getUUID(), type.cooldownType());
            if (remaining > 0) {
                messages.send(player, "cooldown.wait", MessageService.vars("time", TimeUtil.formatSeconds(remaining)));
                return Result.FAILED;
            }
        }

        CostManager.CostResult cost = null;
        boolean costBypass = Permissions.has(player, "cost.bypass", false);
        if (!costBypass && (config.cost.exp().enabled() || config.cost.item().enabled())) {
            cost = costs.calculate(player, before, requested);
            if (!cost.affordable()) {
                messages.send(player, "cost.not_enough", MessageService.vars(
                        "exp", cost.expCost(), "items", cost.itemCost()));
                return Result.FAILED;
            }
        }

        TeleportPosition destination = requested;
        if (config.safety.enabled() && !skipSafety && !Permissions.has(player, "safety.bypass", false)) {
            SafetyChecker.Result result = safety.check(destinationLevel, destination);
            if (!result.safe()) {
                if (config.safety.mode() == TeleportConfig.SafetyMode.NEARBY_SAFE) {
                    TeleportPosition nearby = safety.findNearby(destinationLevel, destination);
                    if (nearby != null) {
                        destination = nearby;
                        messages.send(player, "safety.moved");
                    } else if (config.safety.nearbyFallback() == TeleportConfig.NearbyFallback.CONFIRM) {
                        return requestConfirm(player, destination, type, result.reason());
                    } else {
                        messages.send(player, "safety.denied");
                        return Result.FAILED;
                    }
                } else {
                    return requestConfirm(player, destination, type, result.reason());
                }
            }
        }

        boolean success = player.teleportTo(destinationLevel, destination.x(), destination.y(), destination.z(),
                Set.of(), destination.yaw(), destination.pitch(), true);
        if (!success) {
            messages.send(player, "teleport.failed");
            return Result.FAILED;
        }

        backs.set(player.getUUID(), before);
        if (!cooldownBypass) {
            int seconds = switch (type.cooldownType()) {
                case WAYPOINT -> config.cooldown.waypointSeconds();
                case TP -> config.cooldown.tpSeconds();
                case BACK -> config.cooldown.backSeconds();
                case RANDOM -> config.cooldown.rtpSeconds();
            };
            cooldowns.set(player.getUUID(), type.cooldownType(), seconds);
        }
        if (!costBypass && cost != null) costs.apply(player, cost);

        messages.send(player, "back.saved", MessageService.vars("button",
                messages.button(player, "back.button", "/rime back", "back.button", false)));
        return Result.SUCCESS;
    }

    private Result requestConfirm(ServerPlayer player, TeleportPosition destination, TeleportType type, String reason) {
        confirms.register(new ConfirmManager.PendingTeleport(player.getUUID(), destination, type,
                System.currentTimeMillis() + config.confirmTimeoutSeconds * 1000L));
        String reasonText = messages.resolveMessage(player, "safety.reason." + reason);
        messages.send(player, "safety.confirm", MessageService.vars(
                "reason", reasonText,
                "confirm", messages.component(player, "safety.button.confirm"),
                "cancel", messages.component(player, "safety.button.cancel")));
        return Result.PENDING_CONFIRM;
    }

    public enum Result {SUCCESS, FAILED, PENDING_CONFIRM}
}
