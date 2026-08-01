package org.rimecraft.rimetools.module.punishment;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.punishment.command.PunishmentCommands;
import org.rimecraft.rimetools.module.punishment.config.PunishmentConfig;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRepository;
import org.rimecraft.rimetools.module.punishment.network.MuteNoticePayload;
import org.rimecraft.rimetools.module.punishment.network.PunishmentNetworking;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Persistent moderation, login enforcement, commands and client administration. */
public final class PunishmentModule implements RimeModule {
    public static final String ID = "punishment";
    public static PunishmentModule INSTANCE;

    private final Map<UUID, Long> lastMuteNotice = new ConcurrentHashMap<>();
    private PunishmentRepository repository;
    private PunishmentConfig config;
    private PunishmentService service;
    private Logger logger;
    private MinecraftServer server;
    private Path configPath;
    private ExecutorService repositoryWriter;

    @Override
    public String id() { return ID; }

    @Override
    public void initialize(RimeModuleContext context) {
        INSTANCE = this;
        logger = context.logger();
        configPath = context.configFile(ID);
        reloadConfig();
        service = new PunishmentService(this);
        PayloadTypeRegistry.clientboundPlay().register(MuteNoticePayload.TYPE, MuteNoticePayload.STREAM_CODEC);
        PunishmentNetworking.register(this);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) ->
                new PunishmentCommands(this).register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> {
            server = starting;
            repositoryWriter = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "rime-tools-punishment-writer");
                thread.setDaemon(true);
                return thread;
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            Path dataDirectory = context.moduleDirectory(ID);
            try {
                Files.createDirectories(dataDirectory);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to create the punishment data directory", exception);
            }
            repository = new PunishmentRepository(dataDirectory, logger, repositoryWriter);
            if (!repository.load()) throw new IllegalStateException("Failed to load punishment records");
            repository.saveIfDirty(); // persists an automatic v1 -> v2 migration
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            if (repository != null) repository.saveIfDirty();
            if (repositoryWriter != null) {
                repositoryWriter.shutdown();
                try {
                    if (!repositoryWriter.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("Timed out waiting for punishment data to finish saving");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while waiting for punishment data to finish saving");
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            repository = null;
            repositoryWriter = null;
            server = null;
            lastMuteNotice.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            if (repository != null && ticking.getTickCount() % 20 == 0) repository.saveIfDirty();
        });
    }

    public boolean reloadConfig() {
        PunishmentConfig loaded = PunishmentConfig.load(configPath);
        try {
            loaded.save(configPath);
            config = loaded;
            return true;
        } catch (Exception exception) {
            logger.warn("Could not write punishment module configuration; in-memory values remain active", exception);
            config = loaded;
            return false;
        }
    }

    public MinecraftServer server() { return server; }
    public PunishmentRepository repository() { return repository; }
    public PunishmentConfig config() { return config; }
    public PunishmentService service() { return service; }

    public Optional<PunishmentRecord> activeBan(UUID playerId) {
        return repository == null ? Optional.empty() : service.activeBan(playerId);
    }

    public Optional<PunishmentRecord> activeMute(UUID playerId) {
        return repository == null ? Optional.empty() : service.activeMute(playerId);
    }

    /** Broadcasts a readable audit summary, excluding the player who executed the action. */
    public void announce(PunishmentRecord record) {
        if (server == null || config == null || !config.announcePunishments()) return;
        String reason = record.reason() == null ? "-" : record.reason();
        var message = net.minecraft.network.chat.Component.translatableWithFallback(
                "rime-tools.punish.announce",
                record.type() + ": " + record.playerName() + " — by " + record.executor() + " — " + reason,
                record.type(), record.playerName(), record.executor(), reason);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().name().equalsIgnoreCase(record.executor())) continue;
            player.sendSystemMessage(message);
        }
    }

    public void notifyMuted(ServerPlayer player, PunishmentRecord record, long remainingSeconds) {
        notifyMuted(player, remainingSeconds, PunishmentText.muted(record, remainingSeconds));
    }

    /** Rate-limited text plus optional enhanced-client HUD notice. */
    public void notifyMuted(ServerPlayer player, long remainingSeconds) {
        notifyMuted(player, remainingSeconds, PunishmentText.muted(remainingSeconds));
    }

    private void notifyMuted(ServerPlayer player, long remainingSeconds, net.minecraft.network.chat.Component message) {
        long now = System.currentTimeMillis() / 1000;
        long cooldown = config == null ? 3 : config.muteNoticeCooldownSeconds();
        Long previous = lastMuteNotice.put(player.getUUID(), now);
        if (previous == null || now - previous >= cooldown) {
            player.sendSystemMessage(message);
            if (ServerPlayNetworking.canSend(player, MuteNoticePayload.TYPE)) {
                ServerPlayNetworking.send(player, new MuteNoticePayload(remainingSeconds));
            }
        }
    }
}
