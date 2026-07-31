package org.rimecraft.rimetools.module.punishment.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.rimecraft.rimetools.module.punishment.PunishmentClearRankService;
import org.rimecraft.rimetools.module.punishment.PunishmentModule;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;
import org.rimecraft.rimetools.module.punishment.util.DurationParser;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class PunishmentCommands {
    private static final Identifier PUNISH = Identifier.fromNamespaceAndPath("rime-tools", "punish");

    private final PunishmentModule mod;

    public PunishmentCommands(PunishmentModule mod) {
        this.mod = mod;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var player = Commands.argument("player", GameProfileArgument.gameProfile());
        var reason = Commands.argument("reason", StringArgumentType.greedyString());
        var duration = Commands.argument("duration", StringArgumentType.word());

        dispatcher.register(Commands.literal("punish")
                .then(Commands.literal("ban").requires(this::hasPermission)
                        .then(player
                                .executes(ctx -> ban(ctx.getSource(), profile(ctx), null))
                                .then(reason.executes(ctx -> ban(ctx.getSource(), profile(ctx), reason(ctx))))))
                .then(Commands.literal("tempban").requires(this::hasPermission)
                        .then(player.then(duration
                                .executes(ctx -> tempBan(ctx.getSource(), profile(ctx), duration(ctx), null))
                                .then(reason.executes(ctx -> tempBan(ctx.getSource(), profile(ctx), duration(ctx), reason(ctx)))))))
                .then(Commands.literal("mute").requires(this::hasPermission)
                        .then(player.then(duration
                                .executes(ctx -> mute(ctx.getSource(), profile(ctx), duration(ctx), null))
                                .then(reason.executes(ctx -> mute(ctx.getSource(), profile(ctx), duration(ctx), reason(ctx)))))))
                .then(Commands.literal("kick").requires(this::hasPermission)
                        .then(player
                                .executes(ctx -> kick(ctx.getSource(), profile(ctx), null))
                                .then(reason.executes(ctx -> kick(ctx.getSource(), profile(ctx), reason(ctx))))))
                .then(Commands.literal("clearrank").requires(this::hasPermission)
                        .then(player.then(Commands.argument("period", StringArgumentType.word())
                                .executes(ctx -> clearRank(ctx.getSource(), profile(ctx), period(ctx))))))
                .then(Commands.literal("unban").requires(this::hasPermission)
                        .then(player.executes(ctx -> revoke(ctx.getSource(), profile(ctx), PunishmentRecord.Type.PERMA_BAN, PunishmentRecord.Type.TEMP_BAN))))
                .then(Commands.literal("unmute").requires(this::hasPermission)
                        .then(player.executes(ctx -> revoke(ctx.getSource(), profile(ctx), PunishmentRecord.Type.MUTE, null))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), null))
                        .then(player.requires(this::hasPermission)
                                .executes(ctx -> list(ctx.getSource(), profile(ctx))))));

        // Override vanilla /kick: record the violation and disconnect. The
        // "targets" argument matches the vanilla node, so this replaces it.
        dispatcher.register(Commands.literal("kick").requires(this::hasPermission)
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> kickMany(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), null))
                        .then(reason.executes(ctx -> kickMany(ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets"), reason(ctx))))));
    }

    private boolean hasPermission(CommandSourceStack source) {
        return ((PermissionContextOwner) (Object) source).checkPermission(PUNISH, PermissionLevel.ADMINS);
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        net.minecraft.server.players.NameAndId entry =
                GameProfileArgument.getGameProfiles(ctx, "player").iterator().next();
        return new GameProfile(entry.id(), entry.name());
    }

    private static String duration(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "duration");
    }

    private static String reason(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "reason");
    }

    private static String period(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "period");
    }

    private int ban(CommandSourceStack source, GameProfile profile, String reason) {
        long now = Instant.now().getEpochSecond();
        record(source, profile, PunishmentRecord.Type.PERMA_BAN, now, 0, reason);
        disconnectIfOnline(profile.id(), Component.translatable("rime-tools.punish.ban.disconnect"));
        return feedback(source, "rime-tools.punish.ban.sent", profile.name());
    }

    private int tempBan(CommandSourceStack source, GameProfile profile, String duration, String reason) {
        long seconds = DurationParser.parseSeconds(duration);
        if (seconds <= 0) return feedback(source, "rime-tools.punish.invalid_duration", duration);
        long now = Instant.now().getEpochSecond();
        record(source, profile, PunishmentRecord.Type.TEMP_BAN, now, now + seconds, reason);
        disconnectIfOnline(profile.id(), Component.translatable("rime-tools.punish.tempban.disconnect", seconds));
        return feedback(source, "rime-tools.punish.tempban.sent", profile.name(), duration);
    }

    private int mute(CommandSourceStack source, GameProfile profile, String duration, String reason) {
        long seconds = DurationParser.parseSeconds(duration);
        if (seconds <= 0) return feedback(source, "rime-tools.punish.invalid_duration", duration);
        long now = Instant.now().getEpochSecond();
        record(source, profile, PunishmentRecord.Type.MUTE, now, now + seconds, reason);
        ServerPlayer online = mod.server() == null ? null : mod.server().getPlayerList().getPlayer(profile.id());
        if (online != null) {
            online.sendSystemMessage(Component.translatable("rime-tools.punish.muted.online", seconds));
        }
        return feedback(source, "rime-tools.punish.mute.sent", profile.name(), duration);
    }

    private int kick(CommandSourceStack source, GameProfile profile, String reason) {
        record(source, profile, PunishmentRecord.Type.KICK, Instant.now().getEpochSecond(), 0, reason);
        ServerPlayer online = mod.server() == null ? null : mod.server().getPlayerList().getPlayer(profile.id());
        if (online != null) {
            online.connection.disconnect(Component.translatable(
                    reason == null ? "rime-tools.punish.kick.disconnect" : "rime-tools.punish.kick.disconnect_reason", reason));
        }
        return feedback(source, "rime-tools.punish.kick.sent", profile.name());
    }

    private int kickMany(CommandSourceStack source, Collection<ServerPlayer> targets, String reason) {
        long now = Instant.now().getEpochSecond();
        for (ServerPlayer target : targets) {
            record(source, target.getGameProfile(), PunishmentRecord.Type.KICK, now, 0, reason);
            target.connection.disconnect(Component.translatable(
                    reason == null ? "rime-tools.punish.kick.disconnect" : "rime-tools.punish.kick.disconnect_reason", reason));
        }
        return feedback(source, "rime-tools.punish.kick.sent", String.valueOf(targets.size()));
    }

    private int clearRank(CommandSourceStack source, GameProfile profile, String period) {
        if (!"week".equalsIgnoreCase(period) && !"month".equalsIgnoreCase(period)) {
            return feedback(source, "rime-tools.punish.invalid_period", period);
        }
        ServerPlayer online = mod.server() == null ? null : mod.server().getPlayerList().getPlayer(profile.id());
        if (online != null) {
            online.connection.disconnect(Component.translatable("rime-tools.punish.clearrank.disconnect"));
        }
        boolean cleared = PunishmentClearRankService.clearRank(source.getServer(), profile.id());
        if (!cleared) return feedback(source, "rime-tools.punish.clearrank.failed", profile.name());
        long now = Instant.now().getEpochSecond();
        record(source, profile, PunishmentRecord.Type.CLEAR_RANK, now, 0, "clearrank " + period);
        return feedback(source, "rime-tools.punish.clearrank.sent", profile.name(), period);
    }

    private int revoke(CommandSourceStack source, GameProfile profile, PunishmentRecord.Type first, PunishmentRecord.Type second) {
        boolean removed = mod.repository().revoke(profile.id(), first);
        if (second != null) removed |= mod.repository().revoke(profile.id(), second);
        mod.repository().saveIfDirty();
        return feedback(source, removed ? "rime-tools.punish.revoked" : "rime-tools.punish.none_active", profile.name());
    }

    private int list(CommandSourceStack source, GameProfile profile) {
        UUID targetId;
        try {
            targetId = profile == null ? source.getPlayerOrException().getUUID() : profile.id();
        } catch (CommandSyntaxException exception) {
            return feedback(source, "rime-tools.punish.not_found");
        }
        List<PunishmentRecord> history = mod.repository().history(targetId);
        if (history.isEmpty()) return feedback(source, "rime-tools.punish.list.empty");
        String summary = String.join(", ", history.stream().limit(10)
                .map(PunishmentCommands::describe)
                .toList());
        return feedback(source, "rime-tools.punish.list", summary);
    }

    private static String describe(PunishmentRecord record) {
        return record.type() + "@" + record.issuedAt()
                + (record.expiresAt() > 0 ? "->" + record.expiresAt() : "")
                + (record.reason() == null ? "" : "(" + record.reason() + ")");
    }

    private void record(CommandSourceStack source, GameProfile profile, PunishmentRecord.Type type,
                        long issuedAt, long expiresAt, String reason) {
        mod.repository().add(new PunishmentRecord(profile.id(), profile.name(), type, issuedAt, expiresAt,
                reason, source.getTextName()));
        mod.repository().saveIfDirty();
    }

    private void disconnectIfOnline(UUID playerId, Component message) {
        ServerPlayer online = mod.server() == null ? null : mod.server().getPlayerList().getPlayer(playerId);
        if (online != null) {
            online.connection.disconnect(message);
        }
    }

    private int feedback(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args), false);
        return 1;
    }
}
