package org.rimecraft.rimetools.module.punishment;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.RimeModule;
import org.rimecraft.rimetools.module.RimeModuleContext;
import org.rimecraft.rimetools.module.punishment.command.PunishmentCommands;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRecord;
import org.rimecraft.rimetools.module.punishment.data.PunishmentRepository;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Punishment module: temporary/permanent bans, temporary mutes, kicks,
 * RankBoard standings clearing and a persistent violation log.
 */
public final class PunishmentModule implements RimeModule {
    public static final String ID = "punishment";
    public static PunishmentModule INSTANCE;

    private PunishmentRepository repository;
    private Logger logger;
    private MinecraftServer server;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(RimeModuleContext context) {
        INSTANCE = this;
        logger = context.logger();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
                .register(org.rimecraft.rimetools.module.punishment.network.MuteNoticePayload.TYPE,
                        org.rimecraft.rimetools.module.punishment.network.MuteNoticePayload.STREAM_CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) ->
                new PunishmentCommands(this).register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> this.server = server);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Path dataDirectory = context.moduleDirectory(ID);
            try {
                Files.createDirectories(dataDirectory);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to create the punishment data directory", exception);
            }
            repository = new PunishmentRepository(dataDirectory, logger);
            if (!repository.load()) {
                throw new IllegalStateException("Failed to load punishment records");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (repository != null) repository.saveIfDirty();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (repository != null && server.getTickCount() % 20 == 0) {
                repository.saveIfDirty();
            }
        });
    }

    public MinecraftServer server() {
        return server;
    }

    public PunishmentRepository repository() {
        return repository;
    }

    public Optional<PunishmentRecord> activeBan(UUID playerId) {
        if (repository == null) return Optional.empty();
        return repository.activeBan(playerId, Instant.now().getEpochSecond());
    }

    public Optional<PunishmentRecord> activeMute(UUID playerId) {
        if (repository == null) return Optional.empty();
        return repository.activeMute(playerId, Instant.now().getEpochSecond());
    }

    /** Notifies a muted player that their message was blocked. */
    public void notifyMuted(ServerPlayer player, long remainingSeconds) {
        player.sendSystemMessage(Component.translatable("rime-tools.punish.muted.online", remainingSeconds));
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,
                org.rimecraft.rimetools.module.punishment.network.MuteNoticePayload.TYPE)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new org.rimecraft.rimetools.module.punishment.network.MuteNoticePayload(remainingSeconds));
        }
    }
}
