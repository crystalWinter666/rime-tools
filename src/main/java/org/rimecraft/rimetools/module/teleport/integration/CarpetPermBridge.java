package org.rimecraft.rimetools.module.teleport.integration;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.carpet.api.CarpetPermApi;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.model.FakePlayerInfo;

import java.util.*;

/**
 * Bridges the teleport module to the built-in Carpet fake-player API.
 */
public final class CarpetPermBridge {

    private CarpetPermBridge() {
    }

    public static List<FakePlayerInfo> listFakePlayers(MinecraftServer server, UUID viewerUuid) {
        if (server == null) return List.of();
        List<FakePlayerInfo> result = new ArrayList<>();
        for (var online : server.getPlayerList().getPlayers()) {
            findFakePlayer(server, online.getGameProfile().name(), viewerUuid).ifPresent(result::add);
        }
        result.sort(Comparator.comparing(FakePlayerInfo::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static Optional<FakePlayerInfo> findFakePlayer(MinecraftServer server, String name, UUID viewerUuid) {
        if (server == null || name == null) return Optional.empty();
        try {
            Optional<CarpetPermApi.FakePlayerPosition> positionResult =
                    CarpetPermApi.getFakePlayerPosition(server, name);
            if (positionResult.isEmpty()) return Optional.empty();

            CarpetPermApi.FakePlayerPosition positionRecord = positionResult.orElseThrow();
            ResourceKey<?> dimension = positionRecord.dimension();
            Vec3 position = positionRecord.position();

            String creatorName = null;
            boolean owned = false;
            Optional<CarpetPermApi.FakePlayerCreator> creatorResult =
                    CarpetPermApi.getFakePlayerCreator(server, name);
            if (creatorResult.isPresent()) {
                CarpetPermApi.FakePlayerCreator creatorRecord = creatorResult.orElseThrow();
                creatorName = creatorRecord.name();
                owned = creatorRecord.playerUuid().filter(viewerUuid::equals).isPresent();
            }

            return Optional.of(new FakePlayerInfo(
                    name,
                    TeleportConfig.normalizeWorld(dimension.identifier().toString()),
                    position.x(), position.y(), position.z(), creatorName, owned));
        } catch (LinkageError | RuntimeException exception) {
            RimeTools.LOGGER.warn("Failed to query Carpet fake player {}", name, exception);
            return Optional.empty();
        }
    }
}
