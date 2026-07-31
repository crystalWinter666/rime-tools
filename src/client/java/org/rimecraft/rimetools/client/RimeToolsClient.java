package org.rimecraft.rimetools.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.client.module.ClientModuleContext;
import org.rimecraft.rimetools.client.module.ClientModuleRegistry;
import org.rimecraft.rimetools.client.module.punishment.PunishmentClientModule;
import org.rimecraft.rimetools.client.module.teleport.TeleportClientModule;
import org.rimecraft.rimetools.client.module.title.TitleClientModule;

import java.nio.file.Path;

public final class RimeToolsClient implements ClientModInitializer {
    private final ClientModuleRegistry moduleRegistry = new ClientModuleRegistry();

    @Override
    public void onInitializeClient() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve(RimeTools.MOD_ID);
        ClientModuleContext context = new ClientModuleContext(configDirectory, RimeTools.LOGGER, moduleRegistry);
        moduleRegistry.register(new TeleportClientModule());
        moduleRegistry.register(new TitleClientModule());
        moduleRegistry.register(new PunishmentClientModule());
        moduleRegistry.modules().forEach(module -> {
            try {
                module.initializeClient(context);
            } catch (Exception exception) {
                RimeTools.LOGGER.error("Failed to initialize client module {}; continuing with other modules", module.id(), exception);
            }
        });
    }
}
