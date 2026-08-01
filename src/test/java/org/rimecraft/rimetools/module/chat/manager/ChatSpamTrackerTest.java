package org.rimecraft.rimetools.module.chat.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void detectsRepeatedAndSimilarMessages() {
        ChatSpamTracker tracker = new ChatSpamTracker();
        UUID player = UUID.randomUUID();
        assertEquals(ChatSpamTracker.Violation.NONE,
                tracker.record(player, "Selling diamonds now", 1000, 20, 5,
                        true, 20, 2, 0.8).violation());
        assertEquals(ChatSpamTracker.Violation.NONE,
                tracker.record(player, "selling  diamonds now", 1001, 20, 5,
                        true, 20, 2, 0.8).violation());
        assertEquals(ChatSpamTracker.Violation.DUPLICATE,
                tracker.record(player, "Selling diamonds now!", 1002, 20, 5,
                        true, 20, 2, 0.8).violation());
    }

    @Test
    void cleanupDropsInactivePlayers() {
        ChatSpamTracker tracker = new ChatSpamTracker();
        tracker.record(UUID.randomUUID(), "hello", 1000, 5, 5, false, 20, 2, 0.9);
        assertEquals(1, tracker.trackedPlayers());
        tracker.cleanup(2000, 60);
        assertEquals(0, tracker.trackedPlayers());
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
