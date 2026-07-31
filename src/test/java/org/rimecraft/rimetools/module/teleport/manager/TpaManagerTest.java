package org.rimecraft.rimetools.module.teleport.manager;

import org.junit.jupiter.api.Test;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TpaManagerTest {
    @Test
    void enforcesRequestCooldown() {
        TpaManager manager = new TpaManager();
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        long now = 1000;
        manager.add(new TpaManager.TpaRequest(sender, target, TpaManager.Type.TO_TARGET, now, now + 60),
                TeleportConfig.DuplicatePolicy.REJECT);

        assertEquals(5, manager.cooldownRemaining(sender, target, now, 5));
        assertEquals(0, manager.cooldownRemaining(sender, target, now + 5, 5));
        assertEquals(0, manager.cooldownRemaining(sender, target, now, 0));
        assertEquals(0, manager.cooldownRemaining(UUID.randomUUID(), target, now, 5));
    }

    @Test
    void limitsTargetChatMessagesWithinWindow() {
        TpaManager manager = new TpaManager();
        UUID target = UUID.randomUUID();
        long now = 1000;

        assertTrue(manager.allowTargetChat(target, now, 3, 10));
        assertTrue(manager.allowTargetChat(target, now + 1, 3, 10));
        assertTrue(manager.allowTargetChat(target, now + 2, 3, 10));
        assertFalse(manager.allowTargetChat(target, now + 3, 3, 10));
        assertTrue(manager.allowTargetChat(target, now + 11, 3, 10));
        assertTrue(manager.allowTargetChat(target, now + 12, 3, 10));
        assertTrue(manager.allowTargetChat(target, now, 0, 10));
    }
}
