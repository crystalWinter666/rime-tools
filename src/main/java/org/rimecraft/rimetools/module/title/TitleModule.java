package org.rimecraft.rimetools.module.title;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.chat.ChatNameDecoration;
import org.rimecraft.rimetools.module.title.chat.TitleChatFormatter;
import org.rimecraft.rimetools.module.title.command.TitleCommands;
import org.rimecraft.rimetools.module.title.config.TitleConfig;
import org.rimecraft.rimetools.module.title.integration.RankBoardAwardService;
import org.rimecraft.rimetools.module.title.network.TitleNetworking;
import org.rimecraft.rimetools.module.title.permission.PermissionChecker;
import org.rimecraft.rimetools.module.title.placeholder.TitlePlaceholders;
import org.rimecraft.rimetools.module.title.storage.TitleRepository;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TitleModule implements RimeModule {
    public static final String ID = "title";

    private static final ResourceKey<ChatType> TITLE_CHAT = ResourceKey.create(
            Registries.CHAT_TYPE, Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, ID));

    private static TitleRepository repository;
    private static PermissionChecker permissionChecker = PermissionChecker.NONE;
    private RankBoardAwardService rankBoardAwards;

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

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(RimeModuleContext context) {
        ChatNameDecoration.setChatType(TITLE_CHAT);
        ChatNameDecoration.register((sender, name) -> {
            TitleRepository current = repository;
            if (current == null) return name;
            Component title = current.findVisibleTitle(sender, permissionChecker)
                    .map(TitleDefinition::asComponent)
                    .orElseGet(current::fallbackComponent);
            return TitleChatFormatter.decorateSender(title, name);
        });
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
}
