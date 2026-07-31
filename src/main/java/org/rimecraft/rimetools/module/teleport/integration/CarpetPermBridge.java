package org.rimecraft.rimetools.module.teleport.integration;

import org.rimecraft.rimetools.RimeTools;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.model.FakePlayerInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CarpetPermBridge {

    private static final ApiMethods API = loadApi();

    private CarpetPermBridge() {
    }

    public static List<FakePlayerInfo> listFakePlayers(MinecraftServer server, UUID viewerUuid) {
        if (API == null || server == null) return List.of();
        List<FakePlayerInfo> result = new ArrayList<>();
        for (var online : server.getPlayerList().getPlayers()) {
            findFakePlayer(server, online.getGameProfile().name(), viewerUuid).ifPresent(result::add);
        }
        result.sort(Comparator.comparing(FakePlayerInfo::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static Optional<FakePlayerInfo> findFakePlayer(MinecraftServer server, String name, UUID viewerUuid) {
        if (API == null || server == null || name == null) return Optional.empty();
        try {
            Optional<?> positionResult = optional(API.getPosition().invoke(null, server, name));
            if (positionResult.isEmpty()) return Optional.empty();

            Object positionRecord = positionResult.orElseThrow();
            ResourceKey<?> dimension = (ResourceKey<?>) API.positionDimension().invoke(positionRecord);
            Vec3 position = (Vec3) API.positionValue().invoke(positionRecord);

            String creatorName = null;
            boolean owned = false;
            Optional<?> creatorResult = optional(API.getCreator().invoke(null, server, name));
            if (creatorResult.isPresent()) {
                Object creatorRecord = creatorResult.orElseThrow();
                creatorName = (String) API.creatorName().invoke(creatorRecord);
                Optional<?> creatorUuid = optional(API.creatorUuid().invoke(creatorRecord));
                owned = creatorUuid.filter(viewerUuid::equals).isPresent();
            }

            return Optional.of(new FakePlayerInfo(
                    name,
                    TeleportConfig.normalizeWorld(dimension.identifier().toString()),
                    position.x(), position.y(), position.z(), creatorName, owned));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            RimeTools.LOGGER.warn("Failed to query CarpetPerm fake player {}", name, exception);
            return Optional.empty();
        }
    }

    private static Optional<?> optional(Object value) {
        return value instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private static ApiMethods loadApi() {
        if (!FabricLoader.getInstance().isModLoaded("carpetperm")) return null;
        try {
            Class<?> api = Class.forName("org.rimecraft.carpetperm.api.CarpetPermApi");
            Class<?> position = Class.forName("org.rimecraft.carpetperm.api.CarpetPermApi$FakePlayerPosition");
            Class<?> creator = Class.forName("org.rimecraft.carpetperm.api.CarpetPermApi$FakePlayerCreator");
            return new ApiMethods(
                    api.getMethod("getFakePlayerPosition", MinecraftServer.class, String.class),
                    api.getMethod("getFakePlayerCreator", MinecraftServer.class, String.class),
                    position.getMethod("dimension"), position.getMethod("position"),
                    creator.getMethod("name"), creator.getMethod("playerUuid"));
        } catch (ReflectiveOperationException exception) {
            RimeTools.LOGGER.error("CarpetPerm is loaded but its fake-player API is unavailable", exception);
            return null;
        }
    }

    private record ApiMethods(Method getPosition, Method getCreator,
                              Method positionDimension, Method positionValue,
                              Method creatorName, Method creatorUuid) {
    }
}
