package org.rimecraft.rimetools.module.chat;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Applies registered name decorations and the chat-type override to a broadcast.
 */
public final class ChatFormatting {
    private ChatFormatting() {
    }

    public static ChatType.Bound decorate(ChatType.Bound original, ServerPlayer sender) {
        Component name = ChatNameDecoration.decorateSender(sender, sender.getDisplayName());
        ResourceKey<ChatType> override = ChatNameDecoration.chatTypeOverride();
        var registry = sender.level().getServer().registryAccess().lookupOrThrow(Registries.CHAT_TYPE);
        var chatType = override == null
                ? original.chatType()
                : registry.get(override).map(value -> (net.minecraft.core.Holder<ChatType>) value)
                  .orElse(original.chatType());
        return new ChatType.Bound(chatType, name, Optional.empty());
    }
}
