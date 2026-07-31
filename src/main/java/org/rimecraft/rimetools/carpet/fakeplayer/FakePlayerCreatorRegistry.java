package org.rimecraft.rimetools.carpet.fakeplayer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.carpet.api.CarpetPermApi.FakePlayerCreator;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks creators of Carpet fake players spawned via {@code player ... spawn}
 * / {@code player ... shadow} during the current server session.
 */
public final class FakePlayerCreatorRegistry {
    private static final Map<String, FakePlayerCreator> PENDING_CREATORS = new ConcurrentHashMap<>();
    private static final Map<UUID, FakePlayerCreator> CREATORS = new ConcurrentHashMap<>();

    private FakePlayerCreatorRegistry() {
    }

    public static void recordPending(String fakePlayerName, CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Optional<UUID> playerUuid = player == null ? Optional.empty() : Optional.of(player.getUUID());
        PENDING_CREATORS.put(
                normalize(fakePlayerName),
                new FakePlayerCreator(source.getTextName(), playerUuid)
        );
    }

    public static void attach(ServerPlayer fakePlayer) {
        FakePlayerCreator creator = PENDING_CREATORS.remove(
                normalize(fakePlayer.getGameProfile().name())
        );
        if (creator != null) {
            CREATORS.put(fakePlayer.getUUID(), creator);
        }
    }

    public static Optional<FakePlayerCreator> get(UUID fakePlayerUuid) {
        return Optional.ofNullable(CREATORS.get(fakePlayerUuid));
    }

    private static String normalize(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
