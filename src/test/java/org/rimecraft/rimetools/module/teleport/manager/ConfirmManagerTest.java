package org.rimecraft.rimetools.module.teleport.manager;

import org.junit.jupiter.api.Test;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.rimecraft.rimetools.module.teleport.teleport.TeleportType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfirmManagerTest {
    private static ConfirmManager.PendingTeleport pending(UUID playerId, long expiresAtMillis) {
        TeleportPosition destination = new TeleportPosition("world", 1, 64, 2, 0, 0);
        return new ConfirmManager.PendingTeleport(
                playerId, destination, TeleportType.WAYPOINT_GLOBAL, expiresAtMillis);
    }

    @Test
    void pendingTeleportCanOnlyBeTakenOnce() {
        ConfirmManager manager = new ConfirmManager();
        UUID playerId = UUID.randomUUID();
        ConfirmManager.PendingTeleport pending = pending(playerId, System.currentTimeMillis() + 60_000);
        manager.register(pending);

        assertEquals(pending, manager.get(playerId));
        assertEquals(pending, manager.take(playerId));
        assertNull(manager.take(playerId));
    }

    @Test
    void expiredTeleportCannotBeTaken() {
        ConfirmManager manager = new ConfirmManager();
        UUID playerId = UUID.randomUUID();
        manager.register(pending(playerId, System.currentTimeMillis() - 1));

        assertNull(manager.take(playerId));
        assertNull(manager.get(playerId));
    }
}
