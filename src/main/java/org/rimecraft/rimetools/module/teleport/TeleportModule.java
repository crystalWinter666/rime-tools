package org.rimecraft.rimetools.module.teleport;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.teleport.command.TeleportCommands;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.i18n.MessageService;
import org.rimecraft.rimetools.module.teleport.manager.*;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.rimecraft.rimetools.module.teleport.network.TeleportNetworking;
import org.rimecraft.rimetools.module.teleport.repository.OfflinePositionRepository;
import org.rimecraft.rimetools.module.teleport.repository.RepositoryWriter;
import org.rimecraft.rimetools.module.teleport.repository.TpaAllowlistRepository;
import org.rimecraft.rimetools.module.teleport.repository.WaypointRepository;
import org.rimecraft.rimetools.module.teleport.safety.SafetyChecker;
import org.rimecraft.rimetools.module.teleport.teleport.RandomTeleportService;
import org.rimecraft.rimetools.module.teleport.teleport.TeleportService;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TeleportModule implements RimeModule {
    public static final String ID = "teleport";

    private final BackManager backs = new BackManager();
    private final CooldownManager cooldowns = new CooldownManager();
    private final ConfirmManager confirms = new ConfirmManager();
    private final TpaManager tpa = new TpaManager();
    private Logger logger;
    private Path dataDirectory;
    private Path configFile;
    private MinecraftServer server;
    private TeleportConfig config;
    private MessageService messages;
    private WaypointRepository waypoints;
    private TpaAllowlistRepository allowlist;
    private OfflinePositionRepository offlinePositions;
    private TeleportService teleports;
    private RandomTeleportService randomTeleports;
    private RepositoryWriter repositoryWriter;
    private long ticks;

    private static void requireLoaded(boolean loaded, String dataName) {
        if (!loaded) throw new IllegalStateException("Failed to load " + dataName);
    }

    private static void migrateLegacyConfig(RimeModuleContext context, Path configFile) {
        Path legacy = context.moduleDirectory(TeleportModule.ID).resolve("config.yml");
        if (Files.exists(configFile) || !Files.exists(legacy)) {
            return;
        }
        try {
            Files.createDirectories(configFile.getParent());
            Files.copy(legacy, configFile);
            context.logger().info("Migrated legacy configuration from {} to {}", legacy, configFile);
        } catch (Exception exception) {
            context.logger().warn("Could not migrate legacy configuration from {}: {}", legacy, exception.toString());
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(RimeModuleContext context) {
        logger = context.logger();
        dataDirectory = context.moduleDirectory(id());
        configFile = context.configFile(TeleportModule.ID);
        repositoryWriter = new RepositoryWriter(logger);
        try {
            Files.createDirectories(dataDirectory);
            migrateLegacyConfig(context, configFile);
            config = TeleportConfig.load(configFile);
            messages = new MessageService(dataDirectory, config.defaultLocale);
            messages.load();
            waypoints = new WaypointRepository(dataDirectory, context.logger(), repositoryWriter);
            requireLoaded(waypoints.load(), "waypoint data");
            allowlist = new TpaAllowlistRepository(dataDirectory, context.logger(), repositoryWriter);
            requireLoaded(allowlist.load(), "TPA allowlist");
            offlinePositions = new OfflinePositionRepository(dataDirectory, context.logger(), repositoryWriter,
                    config.offlinePlayerRetentionDays, config.offlinePlayerMaxEntries, config.offlinePlayerListLimit);
            requireLoaded(offlinePositions.load(), "offline positions");
        } catch (Exception exception) {
            repositoryWriter.close();
            throw new IllegalStateException("Failed to initialize RIME Tools teleport module", exception);
        }

        TeleportCommands commands = new TeleportCommands(this);
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> commands.register(dispatcher));

        TeleportNetworking.registerPayloads();
        TeleportNetworking.registerServerReceivers(this);

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                offlinePositions.set(player.getUUID(), player.getName().getString(),
                        TeleportPosition.from(player), java.time.Instant.now().getEpochSecond());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && config.backOnDeath) {
                backs.set(player.getUUID(), TeleportPosition.from(player));
                messages.send(player, "back.saved", MessageService.vars("button", messages.component(player, "back.button")));
            }
        });
    }

    private void onServerStarting(MinecraftServer server) {
        this.server = server;
        rebuildTeleportPipeline();
    }

    private void onServerStopping(MinecraftServer server) {
        waypoints.saveIfDirty();
        allowlist.saveIfDirty();
        offlinePositions.saveIfDirty();
        repositoryWriter.close();
        this.server = null;
        teleports = null;
        randomTeleports = null;
    }

    private void onServerTick(MinecraftServer server) {
        ticks++;
        if (ticks % 20 == 0) {
            confirms.tick(server, messages);
            tpa.tick(server, messages);
        }
        long saveInterval = Math.max(0, config.saveIntervalSeconds) * 20L;
        if (saveInterval > 0 && ticks % saveInterval == 0) {
            waypoints.saveIfDirty();
            allowlist.saveIfDirty();
            offlinePositions.saveIfDirty();
        }
    }

    public boolean reloadAll() {
        try {
            repositoryWriter.flush();
            TeleportConfig nextConfig = TeleportConfig.load(configFile);
            MessageService nextMessages = new MessageService(dataDirectory, nextConfig.defaultLocale);
            nextMessages.load();

            WaypointRepository nextWaypoints = new WaypointRepository(dataDirectory, logger, repositoryWriter);
            TpaAllowlistRepository nextAllowlist = new TpaAllowlistRepository(dataDirectory, logger, repositoryWriter);
            OfflinePositionRepository nextOfflinePositions = new OfflinePositionRepository(dataDirectory, logger, repositoryWriter,
                    nextConfig.offlinePlayerRetentionDays, nextConfig.offlinePlayerMaxEntries,
                    nextConfig.offlinePlayerListLimit);
            requireLoaded(nextWaypoints.load(), "waypoint data");
            requireLoaded(nextAllowlist.load(), "TPA allowlist");
            requireLoaded(nextOfflinePositions.load(), "offline positions");

            config = nextConfig;
            messages = nextMessages;
            waypoints = nextWaypoints;
            allowlist = nextAllowlist;
            offlinePositions = nextOfflinePositions;
            rebuildTeleportPipeline();
            logger.info("Reloaded teleport data: {} personal waypoints, {} global waypoints",
                    waypoints.countPersonal(), waypoints.countGlobal());
            return true;
        } catch (Exception exception) {
            logger.error("Failed to reload teleport module", exception);
            return false;
        }
    }

    private void rebuildTeleportPipeline() {
        if (server == null) return;
        SafetyChecker safety = new SafetyChecker(config.safety);
        teleports = new TeleportService(server, config, messages, cooldowns,
                new CostManager(config.cost), safety, confirms, backs);
        randomTeleports = new RandomTeleportService(server, config, messages, cooldowns, safety, teleports);
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public MinecraftServer server() {
        return server;
    }

    public TeleportConfig config() {
        return config;
    }

    public MessageService messages() {
        return messages;
    }

    public WaypointRepository waypoints() {
        return waypoints;
    }

    public TpaAllowlistRepository allowlist() {
        return allowlist;
    }

    public OfflinePositionRepository offlinePositions() {
        return offlinePositions;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public RandomTeleportService randomTeleports() {
        return randomTeleports;
    }

    public BackManager backs() {
        return backs;
    }

    public ConfirmManager confirms() {
        return confirms;
    }

    public TpaManager tpa() {
        return tpa;
    }

}
