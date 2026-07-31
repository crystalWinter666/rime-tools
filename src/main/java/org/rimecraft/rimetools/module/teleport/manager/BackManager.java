package org.rimecraft.rimetools.module.teleport.manager;

import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackManager {
    private final Map<UUID, TeleportPosition> positions = new ConcurrentHashMap<>();

    public void set(UUID uuid, TeleportPosition position) {
        if (position != null) positions.put(uuid, position);
    }

    public TeleportPosition get(UUID uuid) {
        return positions.get(uuid);
    }

    public void clear(UUID uuid) {
        positions.remove(uuid);
    }
}
