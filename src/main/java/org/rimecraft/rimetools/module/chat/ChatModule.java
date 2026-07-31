package org.rimecraft.rimetools.module.chat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.chat.config.ChatConfig;
import org.rimecraft.rimetools.module.chat.manager.ChatMuteManager;
import org.rimecraft.rimetools.module.chat.manager.ChatSpamTracker;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Chat module: unified chat handling for sender-name decoration and
 * anti-spam (mute or kick players flooding the chat).
 */
public final class ChatModule implements RimeModule {
    public static final String ID = "chat";
    public static ChatModule INSTANCE;

    private final ChatMuteManager mutes = new ChatMuteManager();
    private final ChatSpamTracker spam = new ChatSpamTracker();
    private ChatConfig config;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(RimeModuleContext context) {
        INSTANCE = this;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Path configPath = context.configFile(ID);
            config = ChatConfig.load(configPath);
            try {
                config.save(configPath);
            } catch (Exception exception) {
                context.logger().warn("Could not write chat module configuration; in-memory values remain active", exception);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            config = null;
        });
    }

    public ChatConfig config() {
        return config;
    }

    public ChatMuteManager mutes() {
        return mutes;
    }

    /**
     * Applies anti-spam rules for an incoming chat message. Returns true when
     * the message must be blocked (muted player or penalty triggered).
     */
    public boolean handleChatMessage(ServerPlayer player, long now) {
        UUID playerId = player.getUUID();
        if (mutes.isMuted(playerId, now)) {
            player.sendSystemMessage(Component.translatable(
                    "rime-tools.chat.muted", mutes.remainingSeconds(playerId, now)));
            return true;
        }
        if (config == null || !config.antiSpamEnabled()) {
            return false;
        }
        if (spam.record(playerId, now, config.maxMessages(), config.windowSeconds())) {
            if (config.action() == ChatConfig.AntiSpamAction.KICK) {
                player.connection.disconnect(Component.translatable("rime-tools.chat.kicked"));
            } else {
                mutes.mute(playerId, config.muteSeconds(), now);
                player.sendSystemMessage(Component.translatable(
                        "rime-tools.chat.muted", config.muteSeconds()));
            }
            return true;
        }
        return false;
    }
}
