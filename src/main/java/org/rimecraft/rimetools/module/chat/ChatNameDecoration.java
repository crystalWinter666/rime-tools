package org.rimecraft.rimetools.module.chat;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for sender-name decorations (e.g. a title prefix shown before the
 * player name in chat). Modules contribute decorators during initialization.
 */
public final class ChatNameDecoration {
    private static final List<NameDecorator> DECORATORS = new CopyOnWriteArrayList<>();
    private static volatile ResourceKey<ChatType> chatTypeOverride;
    private ChatNameDecoration() {
    }

    public static void register(NameDecorator decorator) {
        DECORATORS.add(decorator);
    }

    public static Component decorateSender(ServerPlayer sender, Component name) {
        Component result = name;
        for (NameDecorator decorator : DECORATORS) {
            result = decorator.decorate(sender, result);
        }
        return result;
    }

    public static void setChatType(ResourceKey<ChatType> chatType) {
        chatTypeOverride = chatType;
    }

    public static ResourceKey<ChatType> chatTypeOverride() {
        return chatTypeOverride;
    }

    public interface NameDecorator {
        Component decorate(ServerPlayer sender, Component name);
    }
}
