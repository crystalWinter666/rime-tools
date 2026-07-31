package org.rimecraft.rimetools.module.chat.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSpamTrackerTest {
    @Test
    void triggersWhenWindowIsExceededAndResets() {
        ChatSpamTracker tracker = new ChatSpamTracker();
        UUID player = UUID.randomUUID();
        long now = 1000;

        assertFalse(tracker.record(player, now, 3, 5));
        assertFalse(tracker.record(player, now + 1, 3, 5));
        assertFalse(tracker.record(player, now + 2, 3, 5));
        assertTrue(tracker.record(player, now + 3, 3, 5));
        // window was reset, so the next burst counts from scratch
        assertFalse(tracker.record(player, now + 4, 3, 5));
    }

    @Test
    void oldMessagesRollOutOfWindow() {
        ChatSpamTracker tracker = new ChatSpamTracker();
        UUID player = UUID.randomUUID();
        long now = 1000;

        assertFalse(tracker.record(player, now, 3, 5));
        assertFalse(tracker.record(player, now + 1, 3, 5));
        assertFalse(tracker.record(player, now + 11, 3, 5));
        assertFalse(tracker.record(player, now + 12, 3, 5));
        assertFalse(tracker.record(player, now + 13, 3, 5));
        assertTrue(tracker.record(player, now + 14, 3, 5));
    }
}
