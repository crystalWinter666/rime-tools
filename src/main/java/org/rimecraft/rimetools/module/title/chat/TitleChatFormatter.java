package org.rimecraft.rimetools.module.title.chat;

import net.minecraft.network.chat.Component;

public final class TitleChatFormatter {
    private TitleChatFormatter() {
    }

    public static Component format(Component title, Component playerName, Component message) {
        return decorateSender(title, playerName)
                .append(Component.literal(": "))
                .append(message);
    }

    public static net.minecraft.network.chat.MutableComponent decorateSender(Component title, Component playerName) {
        return decorateTitle(title)
                .append(Component.literal(" "))
                .append(playerName);
    }

    public static net.minecraft.network.chat.MutableComponent decorateTitle(Component title) {
        return Component.literal("[ ").setStyle(title.getStyle())
                .append(title)
                .append(Component.literal(" ]").setStyle(title.getStyle()));
    }
}
