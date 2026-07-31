package org.rimecraft.rimetools.module.teleport.manager;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownManager {
    private final Map<UUID, EnumMap<Type, Long>> cooldowns = new ConcurrentHashMap<>();

    public long remaining(UUID uuid, Type type) {
        long until = cooldowns.getOrDefault(uuid, new EnumMap<>(Type.class)).getOrDefault(type, 0L);
        return Math.max(0, until - Instant.now().getEpochSecond());
    }

    public void set(UUID uuid, Type type, int seconds) {
        if (seconds > 0) {
            cooldowns.computeIfAbsent(uuid, ignored -> new EnumMap<>(Type.class))
                    .put(type, Instant.now().getEpochSecond() + seconds);
        }
    }

    public enum Type {WAYPOINT, TP, BACK, RANDOM}
}
