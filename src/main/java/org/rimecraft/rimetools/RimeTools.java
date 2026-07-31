package org.rimecraft.rimetools;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.ModuleRegistry;
import org.rimecraft.rimetools.module.teleport.TeleportModule;
import org.rimecraft.rimetools.module.title.TitleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RimeTools implements ModInitializer {
    public static final String MOD_ID = "rime-tools";
    public static final String MOD_NAME = "RIME 雾凇 服务器工具";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private final ModuleRegistry moduleRegistry = new ModuleRegistry();

    @Override
    public void onInitialize() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        try {
            Files.createDirectories(configDirectory);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create the RIME Tools configuration directory", exception);
        }

        RimeModuleContext context = new RimeModuleContext(configDirectory, LOGGER, moduleRegistry);
        moduleRegistry.register(new TeleportModule());
        moduleRegistry.register(new TitleModule());
        moduleRegistry.modules().forEach(module -> {
            try {
                module.initialize(context);
                LOGGER.info("Initialized {} module", module.id());
            } catch (Exception exception) {
                LOGGER.error("Failed to initialize {} module; continuing with other modules", module.id(), exception);
            }
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
