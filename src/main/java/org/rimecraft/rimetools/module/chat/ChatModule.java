package org.rimecraft.rimetools.module.chat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.chat.config.ChatConfig;
import org.rimecraft.rimetools.module.chat.manager.ChatMuteManager;
import org.rimecraft.rimetools.module.chat.manager.ChatSpamTracker;
import org.rimecraft.rimetools.module.punishment.PunishmentModule;
import org.rimecraft.rimetools.module.punishment.PunishmentPermissions;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;

import java.nio.file.Path;
import java.util.UUID;

/** Unified sender decoration, anti-abuse governance and mute enforcement. */
public final class ChatModule implements RimeModule {
    public static final String ID = "chat";
    public static ChatModule INSTANCE;

    private final ChatMuteManager fallbackMutes = new ChatMuteManager();
    private final ChatSpamTracker spam = new ChatSpamTracker();
    private ChatConfig config;
    private Path configPath;
    private RimeModuleContext context;

    @Override public String id() { return ID; }

    @Override
    public void initialize(RimeModuleContext context) {
        INSTANCE = this;
        this.context = context;
        configPath = context.configFile(ID);
        reloadConfig();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            spam.remove(id);
            fallbackMutes.clear(id);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 1200 == 0 && config != null) {
                spam.cleanup(System.currentTimeMillis() / 1000, config.stateRetentionSeconds());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> config = null);
    }

    public boolean reloadConfig() {
        ChatConfig loaded = ChatConfig.load(configPath);
        config = loaded;
        try {
            loaded.save(configPath);
            return true;
        } catch (Exception exception) {
            context.logger().warn("Could not write chat module configuration; in-memory values remain active", exception);
            return false;
        }
    }

    public ChatConfig config() { return config; }
    public ChatMuteManager mutes() { return fallbackMutes; }

    /** Returns true when an incoming signed chat message must not be broadcast. */
    public boolean handleChatMessage(ServerPlayer player, String message, long now) {
        if (PunishmentPermissions.chatBypass(player)) return false;
        if (isMuted(player, now, true)) return true;
        ChatConfig current = config;
        if (current == null) return false;
        if (message.length() > current.maxMessageLength()) {
            player.sendSystemMessage(Component.translatableWithFallback("rime-tools.chat.too_long",
                    "Your message is too long (maximum " + current.maxMessageLength() + " characters)",
                    current.maxMessageLength()));
            return true;
        }
        if (current.stripFormatting() && message.indexOf('§') >= 0) {
            player.sendSystemMessage(Component.translatableWithFallback("rime-tools.chat.formatting_blocked",
                    "Legacy formatting codes are not allowed in chat"));
            return true;
        }
        if (!current.antiSpamEnabled()) return false;
        ChatSpamTracker.Result result = spam.record(player.getUUID(), message, now,
                current.maxMessages(), current.windowSeconds(), current.duplicateEnabled(),
                current.duplicateWindowSeconds(), current.maxDuplicates(), current.similarityThreshold());
        if (result.violation() == ChatSpamTracker.Violation.NONE) return false;
        applyAutomaticPenalty(player, result.violation(), now, current);
        return true;
    }

    /** Compatibility overload used by older integration code. */
    public boolean handleChatMessage(ServerPlayer player, long now) {
        return handleChatMessage(player, "", now);
    }

    /** Intercepts configured private/team communication commands while muted. */
    public boolean handleCommand(ServerPlayer player, String command, long now) {
        ChatConfig current = config;
        if (current == null || PunishmentPermissions.chatBypass(player)
                || current.allowedWhileMuted(command) || !current.blockedCommunicationCommand(command)) return false;
        return isMuted(player, now, true);
    }

    private boolean isMuted(ServerPlayer player, long now, boolean notify) {
        PunishmentModule punishment = PunishmentModule.INSTANCE;
        if (punishment != null) {
            var active = punishment.activeMute(player.getUUID());
            if (active.isPresent()) {
                PunishmentRecord mute = active.get();
                if (notify) punishment.notifyMuted(player, mute,
                        mute.expiresAt() == 0 ? -1 : Math.max(0, mute.expiresAt() - now));
                return true;
            }
        }
        if (fallbackMutes.isMuted(player.getUUID(), now)) {
            if (notify) player.sendSystemMessage(Component.translatableWithFallback("rime-tools.chat.muted",
                    "You are temporarily muted", fallbackMutes.remainingSeconds(player.getUUID(), now)));
            return true;
        }
        return false;
    }

    private void applyAutomaticPenalty(ServerPlayer player, ChatSpamTracker.Violation violation,
                                       long now, ChatConfig current) {
        String reason = violation == ChatSpamTracker.Violation.DUPLICATE
                ? "Automatic moderation: repeated similar messages"
                : "Automatic moderation: chat rate exceeded";
        PunishmentModule punishment = PunishmentModule.INSTANCE;
        if (punishment != null && punishment.repository() != null) {
            GameProfile profile = player.getGameProfile();
            switch (current.action()) {
                case WARN -> punishment.service().warn(profile, reason, "Chat anti-spam");
                case MUTE -> punishment.service().apply(profile, PunishmentRecord.Type.MUTE,
                        current.muteSeconds(), reason, "Chat anti-spam");
                case KICK -> punishment.service().kick(profile, reason, "Chat anti-spam");
            }
            return;
        }
        // Safe fallback when the independent punishment module did not initialize.
        if (current.action() == ChatConfig.AntiSpamAction.KICK) {
            player.connection.disconnect(Component.translatableWithFallback("rime-tools.chat.kicked",
                    "You were kicked for chat spam"));
        } else if (current.action() == ChatConfig.AntiSpamAction.MUTE) {
            fallbackMutes.mute(player.getUUID(), current.muteSeconds(), now);
            player.sendSystemMessage(Component.translatableWithFallback("rime-tools.chat.muted",
                    "You are muted for " + current.muteSeconds() + " seconds", current.muteSeconds()));
        } else {
            player.sendSystemMessage(Component.translatableWithFallback("rime-tools.chat.warned",
                    "Please slow down and avoid repeated messages"));
        }
    }
}
