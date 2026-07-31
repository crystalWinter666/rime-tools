package org.rimecraft.rimetools.client.module.title;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.rimecraft.rimetools.client.module.ClientModuleContext;
import org.rimecraft.rimetools.client.module.RimeClientModule;
import org.rimecraft.rimetools.client.ui.ClientGuiRegistry;
import org.rimecraft.rimetools.module.title.TitleModule;

public final class TitleClientModule implements RimeClientModule {
    private final KeyMapping openScreenKey = new KeyMapping(
            "rime-tools.title.key.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KeyMapping.Category.MULTIPLAYER
    );

    @Override
    public String id() {
        return TitleModule.ID;
    }

    @Override
    public void initializeClient(ClientModuleContext context) {
        ClientGuiRegistry.register(TitleModule.ID,
                Component.translatable("rime-tools.module.title"),
                () -> Minecraft.getInstance().setScreenAndShow(new TitleScreen(null)));

        TitleClientNetworking.register();
        KeyMappingHelper.registerKeyMapping(openScreenKey);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openScreenKey.consumeClick() && client.player != null) {
                client.setScreenAndShow(new TitleScreen(null));
            }
        });
    }
}
