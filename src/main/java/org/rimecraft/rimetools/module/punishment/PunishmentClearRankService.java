package org.rimecraft.rimetools.module.punishment;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Clears a player's RankBoard standings: deletes the authoritative vanilla
 * stats file and triggers RankBoard's history-cache warmup so the player is
 * dropped from current and future leaderboards. Historic NBT snapshots are
 * intentionally left untouched.
 */
public final class PunishmentClearRankService {
    private PunishmentClearRankService() {
    }

    public static boolean clearRank(MinecraftServer server, UUID playerId) {
        Path statsFile = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(playerId + ".json");
        try {
            Files.deleteIfExists(statsFile);
        } catch (IOException exception) {
            return false;
        }
        return reloadRankBoardCache(server);
    }

    private static boolean reloadRankBoardCache(MinecraftServer server) {
        if (!FabricLoader.getInstance().isModLoaded("rankboard")) {
            return true;
        }
        try {
            Class<?> reader = Class.forName("cn.bamgdam.rankboard.StatReader");
            Method startWarmup = reader.getDeclaredMethod("startWarmup", MinecraftServer.class);
            startWarmup.setAccessible(true);
            startWarmup.invoke(null, server);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }
}
