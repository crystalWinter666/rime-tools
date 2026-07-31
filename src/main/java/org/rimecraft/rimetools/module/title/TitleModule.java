package org.rimecraft.rimetools.module.title;

import org.rimecraft.rimetools.RimeTools;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.title.config.TitleConfig;
import org.rimecraft.rimetools.module.title.integration.RankBoardAwardService;
import org.rimecraft.rimetools.module.title.placeholder.TitlePlaceholders;
import org.rimecraft.rimetools.module.title.permission.PermissionChecker;
import org.rimecraft.rimetools.module.title.storage.TitleRepository;
import org.rimecraft.rimetools.module.title.network.TitleNetworking;
import org.rimecraft.rimetools.module.title.command.TitleCommands;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TitleModule implements RimeModule {
    public static final String ID = "title";

    private static TitleRepository repository;
    private static PermissionChecker permissionChecker = PermissionChecker.NONE;
    private RankBoardAwardService rankBoardAwards;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(RimeModuleContext context) {
        TitleNetworking.register();
        TitlePlaceholders.register();
        if (isLuckPermsAvailable()) {
            TitleCommands.register();
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (rankBoardAwards != null) rankBoardAwards.tick(server, repository, permissionChecker);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.overworld();
            Path configPath = context.configFile(TitleModule.ID);
            migrateLegacyConfig(context, configPath);
            TitleConfig config = TitleConfig.load(configPath);
            try {
                config.save(configPath);
            } catch (Exception exception) {
                context.logger().warn("Could not write title module configuration; in-memory values remain active", exception);
            }
            repository = TitleRepository.from(level, config);
            rankBoardAwards = new RankBoardAwardService(config, context.logger());
            if ((config.weeklyRankAwards().enabled() || config.monthlyRankAwards().enabled())
                    && !rankBoardAwards.available()) {
                context.logger().info("RankBoard is unavailable; weekly and monthly ranking title settlement is disabled");
            }
            if (isLuckPermsAvailable()) {
                permissionChecker = createPermissionChecker();
            } else {
                permissionChecker = PermissionChecker.NONE;
                context.logger().warn("LuckPerms is unavailable; title selection and administration are disabled");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            repository = null;
            permissionChecker = PermissionChecker.NONE;
            rankBoardAwards = null;
        });
    }

    public static TitleRepository repository() {
        return repository;
    }

    public static boolean isLuckPermsAvailable() {
        return FabricLoader.getInstance().isModLoaded("luckperms");
    }

    public static PermissionChecker permissionChecker() {
        return permissionChecker;
    }

    private static void migrateLegacyConfig(RimeModuleContext context, Path configPath) {
        Path legacy = context.configDirectory().resolve("title.properties");
        if (Files.exists(configPath) || !Files.exists(legacy)) {
            return;
        }
        try {
            TitleConfig.fromProperties(legacy).save(configPath);
            context.logger().info("Migrated legacy configuration from {} to {}", legacy, configPath);
        } catch (Exception exception) {
            context.logger().warn("Could not migrate legacy configuration from {}: {}", legacy, exception.toString());
        }
    }

    private static PermissionChecker createPermissionChecker() {
        try {
            Object access = Class.forName("org.rimecraft.rimetools.module.title.permission.LuckPermsTitleAccess")
                    .getMethod("create")
                    .invoke(null);
            return access instanceof PermissionChecker checker ? checker : PermissionChecker.NONE;
        } catch (ReflectiveOperationException | LinkageError exception) {
            RimeTools.LOGGER
                    .error("LuckPerms was detected but its API could not be initialized", exception);
            return PermissionChecker.NONE;
        }
    }
}
