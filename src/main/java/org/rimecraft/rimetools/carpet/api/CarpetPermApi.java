package org.rimecraft.rimetools.carpet.api;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.rimecraft.rimetools.carpet.fakeplayer.FakePlayerCreatorRegistry;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Queries online Carpet fake players: dimension, exact position and creator.
 * Call from the server thread while accessing the player list.
 */
public final class CarpetPermApi {
    private CarpetPermApi() {
    }

    /**
     * Gets the current position of an online Carpet fake player.
     *
     * @param server     the server whose online players are searched
     * @param playerName the fake player's profile name, matched case-insensitively
     * @return the fake player's dimension and exact position, or empty when no online fake player matches
     */
    public static Optional<FakePlayerPosition> getFakePlayerPosition(
            MinecraftServer server,
            String playerName
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerName, "playerName");

        return findFakePlayer(server, playerName)
                .map(player -> new FakePlayerPosition(player.level().dimension(), player.position()));
    }

    /**
     * Gets the command source that spawned an online Carpet fake player during this server session.
     *
     * @param server     the server whose online players are searched
     * @param playerName the fake player's profile name, matched case-insensitively
     * @return the creator, or empty when the fake player or its creator is unknown
     */
    public static Optional<FakePlayerCreator> getFakePlayerCreator(
            MinecraftServer server,
            String playerName
    ) {
        return findFakePlayer(server, playerName)
                .flatMap(player -> FakePlayerCreatorRegistry.get(player.getUUID()));
    }

    private static Optional<ServerPlayer> findFakePlayer(MinecraftServer server, String playerName) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerName, "playerName");

        return server.getPlayerList().getPlayers().stream()
                .filter(EntityPlayerMPFake.class::isInstance)
                .filter(player -> player.getGameProfile().name().equalsIgnoreCase(playerName))
                .findFirst();
    }

    public record FakePlayerPosition(ResourceKey<Level> dimension, Vec3 position) {
        public FakePlayerPosition {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(position, "position");
        }
    }

    public record FakePlayerCreator(String name, Optional<UUID> playerUuid) {
        public FakePlayerCreator {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(playerUuid, "playerUuid");
        }
    }
}
