package org.rimecraft.rimetools.module.chat.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mute state: blocked players cannot send chat messages until expiry.
 */
public final class ChatMuteManager {
    private final Map<UUID, Long> muteUntil = new ConcurrentHashMap<>();

    public boolean isMuted(UUID playerId, long now) {
        Long until = muteUntil.get(playerId);
        if (until == null) return false;
        if (until <= now) {
            muteUntil.remove(playerId);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID playerId, long now) {
        Long until = muteUntil.get(playerId);
        return until == null ? 0 : Math.max(0, until - now);
    }

    public void mute(UUID playerId, int seconds, long now) {
        muteUntil.put(playerId, now + Math.max(0, seconds));
    }

    public void clear(UUID playerId) {
        muteUntil.remove(playerId);
    }
}
