package org.rimecraft.rimetools.module.title.chat;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.module.title.TitleModule;
import org.rimecraft.rimetools.module.title.storage.TitleRepository;

import java.util.Optional;

public final class TitleChatIntegration {
    private static final ResourceKey<ChatType> TITLE_CHAT = ResourceKey.create(
            Registries.CHAT_TYPE, Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, TitleModule.ID));

    private TitleChatIntegration() {
    }

    public static ChatType.Bound decorate(ChatType.Bound original, ServerPlayer sender) {
        TitleRepository repository = TitleModule.repository();
        if (repository == null) {
            return original;
        }
        Component title = repository.findVisibleTitle(sender, TitleModule.permissionChecker())
                .map(value -> value.asComponent())
                .orElseGet(repository::fallbackComponent);
        Component decoratedName = TitleChatFormatter.decorateSender(title, sender.getDisplayName());
        var registry = sender.level().getServer().registryAccess().lookupOrThrow(Registries.CHAT_TYPE);
        var chatType = registry.get(TITLE_CHAT).map(value -> (net.minecraft.core.Holder<ChatType>) value)
                .orElse(original.chatType());
        return new ChatType.Bound(chatType, decoratedName, Optional.empty());
    }
}
