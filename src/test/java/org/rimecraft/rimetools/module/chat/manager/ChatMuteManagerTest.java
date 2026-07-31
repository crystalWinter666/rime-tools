package org.rimecraft.rimetools.module.chat.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatMuteManagerTest {
    @Test
    void muteExpiresAfterSeconds() {
        ChatMuteManager manager = new ChatMuteManager();
        UUID player = UUID.randomUUID();

        assertFalse(manager.isMuted(player, 1000));
        manager.mute(player, 60, 1000);
        assertTrue(manager.isMuted(player, 1050));
        assertEquals(50, manager.remainingSeconds(player, 1010));
        assertFalse(manager.isMuted(player, 1061));
        assertTrue(manager.remainingSeconds(player, 2000) == 0);
    }
}
