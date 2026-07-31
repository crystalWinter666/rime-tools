package org.rimecraft.rimetools.module.chat.manager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window message counter: tracks how many messages each player sent recently.
 */
public final class ChatSpamTracker {
    private final Map<UUID, Deque<Long>> messageTimes = new ConcurrentHashMap<>();

    /**
     * Records a message for the player. Returns true when the player exceeded
     * maxMessages within windowSeconds, in which case the window is reset so
     * the penalty is not triggered again for every following message.
     */
    public boolean record(UUID playerId, long now, int maxMessages, int windowSeconds) {
        Deque<Long> times = messageTimes.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > windowSeconds) times.pollFirst();
        times.addLast(now);
        if (times.size() > maxMessages) {
            times.clear();
            return true;
        }
        return false;
    }
}
