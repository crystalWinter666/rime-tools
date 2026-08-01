package org.rimecraft.rimetools.module.chat.manager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player sliding-window rate and duplicate/similarity tracking. */
public final class ChatSpamTracker {
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    /** Compatibility rate-only API. */
    public boolean record(UUID playerId, long now, int maxMessages, int windowSeconds) {
        return record(playerId, "", now, maxMessages, windowSeconds,
                false, 1, 1, 1).violation() == Violation.RATE;
    }

    public Result record(UUID playerId, String message, long now, int maxMessages, int windowSeconds,
                         boolean duplicateEnabled, int duplicateWindow, int maxDuplicates,
                         double similarityThreshold) {
        State state = states.computeIfAbsent(playerId, ignored -> new State());
        synchronized (state) {
            state.lastActivity = now;
            while (!state.messageTimes.isEmpty() && now - state.messageTimes.peekFirst() > windowSeconds) {
                state.messageTimes.pollFirst();
            }
            state.messageTimes.addLast(now);
            if (state.messageTimes.size() > maxMessages) {
                state.clearWindows();
                return new Result(Violation.RATE);
            }
            if (!duplicateEnabled) return Result.OK;
            while (!state.messages.isEmpty() && now - state.messages.peekFirst().at > duplicateWindow) {
                state.messages.pollFirst();
            }
            String normalized = normalize(message);
            long similar = state.messages.stream().filter(previous ->
                    similarity(normalized, previous.normalized) >= similarityThreshold).count();
            state.messages.addLast(new Message(now, normalized));
            if (similar >= maxDuplicates) {
                state.clearWindows();
                return new Result(Violation.DUPLICATE);
            }
            return Result.OK;
        }
    }

    public void remove(UUID playerId) { states.remove(playerId); }

    public void cleanup(long now, int retentionSeconds) {
        states.entrySet().removeIf(entry -> now - entry.getValue().lastActivity > retentionSeconds);
    }

    public int trackedPlayers() { return states.size(); }

    static String normalize(String message) {
        String normalized = message == null ? ""
                : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        // Similarity detection is advisory; cap its sample to keep the edit-distance work bounded.
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    /** Normalized edit similarity, optimized by an early length bound. */
    static double similarity(String left, String right) {
        if (left.equals(right)) return 1;
        if (left.isEmpty() || right.isEmpty()) return 0;
        int max = Math.max(left.length(), right.length());
        int min = Math.min(left.length(), right.length());
        if ((double) (max - min) / max > 0.5) return 0;
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return 1.0 - (double) previous[right.length()] / max;
    }

    public enum Violation {NONE, RATE, DUPLICATE}
    public record Result(Violation violation) {
        public static final Result OK = new Result(Violation.NONE);
    }
    private record Message(long at, String normalized) { }
    private static final class State {
        private final Deque<Long> messageTimes = new ArrayDeque<>();
        private final Deque<Message> messages = new ArrayDeque<>();
        private long lastActivity;
        private void clearWindows() { messageTimes.clear(); messages.clear(); }
    }
}
