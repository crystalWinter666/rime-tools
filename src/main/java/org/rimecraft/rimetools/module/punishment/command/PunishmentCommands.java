package org.rimecraft.rimetools.module.punishment.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.rimecraft.rimetools.module.chat.ChatModule;
import org.rimecraft.rimetools.module.punishment.DurationFormatter;
import org.rimecraft.rimetools.module.punishment.PunishmentModule;
import org.rimecraft.rimetools.module.punishment.PunishmentPermissions;
import org.rimecraft.rimetools.module.punishment.PunishmentService;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;
import org.rimecraft.rimetools.module.punishment.util.DurationParser;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Brigadier command surface for the moderation service. */
public final class PunishmentCommands {
    private final PunishmentModule mod;

    public PunishmentCommands(PunishmentModule mod) { this.mod = mod; }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("punish")
                .then(Commands.literal("warn").requires(source -> hasApply(source))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> warn(ctx.getSource(), profile(ctx), null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> warn(ctx.getSource(), profile(ctx), reason(ctx))))))
                .then(Commands.literal("ban").requires(source -> hasApply(source))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> apply(ctx.getSource(), profile(ctx), PunishmentRecord.Type.PERMA_BAN, null, null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> apply(ctx.getSource(), profile(ctx), PunishmentRecord.Type.PERMA_BAN, null, reason(ctx))))))
                .then(timed("tempban", PunishmentRecord.Type.TEMP_BAN))
                .then(timed("mute", PunishmentRecord.Type.MUTE))
                .then(Commands.literal("permmute").requires(source -> hasApply(source))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> apply(ctx.getSource(), profile(ctx), PunishmentRecord.Type.PERMA_MUTE, null, null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> apply(ctx.getSource(), profile(ctx), PunishmentRecord.Type.PERMA_MUTE, null, reason(ctx))))))
                .then(Commands.literal("kick").requires(source -> hasApply(source))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> kick(ctx.getSource(), profile(ctx), null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> kick(ctx.getSource(), profile(ctx), reason(ctx))))))
                .then(Commands.literal("clearrank").requires(source -> PunishmentPermissions.has(source, PunishmentPermissions.CLEAR_RANK))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("period", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"week", "month"}, builder))
                                        .executes(ctx -> clearRank(ctx.getSource(), profile(ctx), StringArgumentType.getString(ctx, "period"))))))
                .then(Commands.literal("unban").requires(source -> hasRevoke(source))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> revokeActive(ctx.getSource(), profile(ctx), true))))
                .then(Commands.literal("unmute").requires(source -> hasRevoke(source))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> revokeActive(ctx.getSource(), profile(ctx), false))))
                .then(Commands.literal("revoke").requires(source -> hasRevoke(source))
                        .then(Commands.argument("record", StringArgumentType.word())
                                .executes(ctx -> revokeRecord(ctx.getSource(), StringArgumentType.getString(ctx, "record"), null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> revokeRecord(ctx.getSource(), StringArgumentType.getString(ctx, "record"), reason(ctx))))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), null, 1))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(source -> PunishmentPermissions.has(source, PunishmentPermissions.HISTORY))
                                .executes(ctx -> list(ctx.getSource(), profile(ctx), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> list(ctx.getSource(), profile(ctx), IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("reload").requires(source -> PunishmentPermissions.has(source, PunishmentPermissions.RELOAD))
                        .executes(ctx -> reload(ctx.getSource()))));

        dispatcher.register(Commands.literal("kick").requires(source -> hasApply(source))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> kickMany(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> kickMany(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), reason(ctx))))));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> timed(
            String literal, PunishmentRecord.Type type) {
        return Commands.literal(literal).requires(source -> hasApply(source))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> apply(ctx.getSource(), profile(ctx), type,
                                        StringArgumentType.getString(ctx, "duration"), null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> apply(ctx.getSource(), profile(ctx), type,
                                                StringArgumentType.getString(ctx, "duration"), reason(ctx))))));
    }

    private boolean hasApply(CommandSourceStack source) {
        return PunishmentPermissions.has(source, PunishmentPermissions.APPLY);
    }

    private boolean hasRevoke(CommandSourceStack source) {
        return PunishmentPermissions.has(source, PunishmentPermissions.REVOKE);
    }

    private int warn(CommandSourceStack source, GameProfile target, String reason) {
        mod.service().warn(target, reason, source.getTextName());
        return successAndAnnounce(source, "rime-tools.punish.warn.sent", "Warned " + target.name(), target.name());
    }

    private int apply(CommandSourceStack source, GameProfile target, PunishmentRecord.Type type,
                      String duration, String reason) {
        long seconds = 0;
        if (duration != null) {
            seconds = DurationParser.parseSeconds(duration);
            if (seconds <= 0) return failure(source, "rime-tools.punish.invalid_duration",
                    "Invalid duration: " + duration, duration);
        }
        try {
            mod.service().apply(target, type, seconds, reason, source.getTextName());
        } catch (ArithmeticException exception) {
            return failure(source, "rime-tools.punish.invalid_duration", "Invalid duration: " + duration, duration);
        }
        String key = switch (type) {
            case PERMA_BAN -> "rime-tools.punish.ban.sent";
            case TEMP_BAN -> "rime-tools.punish.tempban.sent";
            case MUTE -> "rime-tools.punish.mute.sent";
            case PERMA_MUTE -> "rime-tools.punish.permmute.sent";
            default -> throw new IllegalArgumentException("Unsupported type " + type);
        };
        String fallback = type + " applied to " + target.name()
                + (duration == null ? "" : " for " + DurationFormatter.format(seconds));
        return duration == null ? successAndAnnounce(source, key, fallback, target.name())
                : successAndAnnounce(source, key, fallback, target.name(), DurationFormatter.format(seconds));
    }

    private int kick(CommandSourceStack source, GameProfile target, String reason) {
        mod.service().kick(target, reason, source.getTextName());
        return successAndAnnounce(source, "rime-tools.punish.kick.sent", "Kicked " + target.name(), target.name());
    }

    private int kickMany(CommandSourceStack source, Collection<ServerPlayer> targets, String reason) {
        for (ServerPlayer target : targets) mod.service().kick(target.getGameProfile(), reason, source.getTextName());
        return successAndAnnounce(source, "rime-tools.punish.kick.sent", "Kicked " + targets.size() + " player(s)", targets.size());
    }

    private int clearRank(CommandSourceStack source, GameProfile target, String period) {
        if (!period.equalsIgnoreCase("week") && !period.equalsIgnoreCase("month")) {
            return failure(source, "rime-tools.punish.invalid_period", "Invalid period: " + period, period);
        }
        ServerPlayer online = mod.server() == null ? null : mod.server().getPlayerList().getPlayer(target.id());
        if (online != null) online.connection.disconnect(Component.translatableWithFallback(
                "rime-tools.punish.clearrank.disconnect", "Clearing leaderboard data; please reconnect"));
        if (mod.service().clearRank(target, period.toLowerCase(Locale.ROOT), source.getTextName()) == null) {
            return failure(source, "rime-tools.punish.clearrank.failed", "Failed to clear leaderboard data for " + target.name(), target.name());
        }
        return successAndAnnounce(source, "rime-tools.punish.clearrank.sent",
                "Cleared " + target.name() + " leaderboard data (" + period + ")", target.name(), period);
    }

    private int revokeActive(CommandSourceStack source, GameProfile target, boolean ban) {
        int changed = mod.service().revokeActive(target.id(), ban, source.getTextName(), null);
        return changed > 0
                ? success(source, "rime-tools.punish.revoked", "Revoked active punishment for " + target.name(), target.name())
                : failure(source, "rime-tools.punish.none_active", target.name() + " has no matching active punishment", target.name());
    }

    private int revokeRecord(CommandSourceStack source, String value, String reason) {
        UUID id;
        try { id = UUID.fromString(value); }
        catch (IllegalArgumentException exception) {
            return failure(source, "rime-tools.punish.invalid_record", "Invalid record ID: " + value, value);
        }
        return mod.service().revokeRecord(id, source.getTextName(), reason)
                ? success(source, "rime-tools.punish.record_revoked", "Revoked record " + id, id)
                : failure(source, "rime-tools.punish.record_not_active", "Record is missing or inactive: " + id, id);
    }

    private int list(CommandSourceStack source, GameProfile target, int page) {
        UUID targetId;
        String targetName;
        try {
            ServerPlayer self = source.getPlayerOrException();
            targetId = target == null ? self.getUUID() : target.id();
            targetName = target == null ? self.getGameProfile().name() : target.name();
        } catch (CommandSyntaxException exception) {
            return failure(source, "rime-tools.punish.not_found", "Player not found");
        }
        List<PunishmentRecord> history = mod.repository().history(targetId);
        if (history.isEmpty()) return success(source, "rime-tools.punish.list.empty", "No moderation records");
        int pageSize = mod.config().historyPageSize();
        int pages = Math.max(1, (history.size() + pageSize - 1) / pageSize);
        if (page > pages) return failure(source, "rime-tools.punish.invalid_page", "Invalid page " + page, page, pages);
        source.sendSuccess(() -> Component.literal("Moderation history for " + targetName + " (" + page + "/" + pages + ")"), false);
        long now = Instant.now().getEpochSecond();
        history.stream().skip((long) (page - 1) * pageSize).limit(pageSize).forEach(record ->
                source.sendSuccess(() -> Component.literal(describe(record, now)), false));
        return 1;
    }

    private static String describe(PunishmentRecord record, long now) {
        return record.id() + "  " + record.type() + "  " + record.status(now)
                + "  by " + record.executor()
                + (record.reason() == null ? "" : "  " + record.reason());
    }

    private int reload(CommandSourceStack source) {
        boolean saved = mod.reloadConfig();
        ChatModule chat = ChatModule.INSTANCE;
        if (chat != null) saved &= chat.reloadConfig();
        return success(source, "rime-tools.punish.reloaded",
                saved ? "Chat and punishment configuration reloaded"
                        : "Configuration reloaded; one or more files could not be normalized");
    }

    private int successAndAnnounce(CommandSourceStack source, String key, String fallback, Object... args) {
        // PunishmentService already emits the detailed audit announcement and excludes the executor.
        source.sendSuccess(() -> Component.translatableWithFallback(key, fallback, args), false);
        return 1;
    }

    private int success(CommandSourceStack source, String key, String fallback, Object... args) {
        source.sendSuccess(() -> Component.translatableWithFallback(key, fallback, args), false);
        return 1;
    }

    private int failure(CommandSourceStack source, String key, String fallback, Object... args) {
        source.sendFailure(Component.translatableWithFallback(key, fallback, args));
        return 0;
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<NameAndId> entries = GameProfileArgument.getGameProfiles(context, "player");
        if (entries.isEmpty()) throw GameProfileArgument.ERROR_UNKNOWN_PLAYER.create();
        NameAndId entry = entries.iterator().next();
        return new GameProfile(entry.id(), entry.name());
    }

    private static String reason(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "reason");
    }
}
