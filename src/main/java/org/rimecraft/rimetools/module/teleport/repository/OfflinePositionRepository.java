package org.rimecraft.rimetools.module.teleport.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.rimecraft.rimetools.module.teleport.model.OfflinePosition;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.Executor;

public final class OfflinePositionRepository {
    private static final Type DATA_TYPE = new TypeToken<Map<String, OfflinePosition>>() { }.getType();

    private final Path file;
    private final Logger logger;
    private final Executor writer;
    private final long retentionSeconds;
    private final int maxEntries;
    private final int listLimit;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, OfflinePosition> positions = new HashMap<>();
    private boolean dirty;
    private boolean saveScheduled;

    public OfflinePositionRepository(Path dataFolder, Logger logger) {
        this(dataFolder, logger, Runnable::run, 0, 0, 0);
    }

    public OfflinePositionRepository(Path dataFolder, Logger logger, Executor writer) {
        this(dataFolder, logger, writer, 0, 0, 0);
    }

    public OfflinePositionRepository(Path dataFolder, Logger logger, Executor writer,
                                     int retentionDays, int maxEntries, int listLimit) {
        file = dataFolder.resolve("offline_positions.json");
        this.logger = logger;
        this.writer = writer;
        retentionSeconds = retentionDays <= 0 ? 0 : retentionDays * 86400L;
        this.maxEntries = Math.max(0, maxEntries);
        this.listLimit = Math.max(0, listLimit);
    }

    public synchronized boolean load() {
        try {
            Map<String, OfflinePosition> loaded = new HashMap<>();
            if (Files.exists(file)) {
                loaded = gson.fromJson(
                        Files.readString(file, StandardCharsets.UTF_8), DATA_TYPE);
                if (loaded == null) throw new IllegalStateException("Offline position file is empty or contains JSON null: " + file);
            }
            positions.clear();
            loaded.forEach((key, value) -> {
                if (key != null && value != null) positions.put(key, value);
            });
            dirty = prune(System.currentTimeMillis() / 1000L);
            return true;
        } catch (Exception exception) {
            logger.error("Failed to load {}; keeping the current in-memory positions", file, exception);
            return false;
        }
    }

    public synchronized void saveIfDirty() {
        if (dirty) scheduleSave();
    }

    public synchronized void save() {
        dirty = true;
        scheduleSave();
    }

    public synchronized void set(UUID playerId, String playerName, TeleportPosition position, long updatedAt) {
        positions.put(playerId.toString(), new OfflinePosition(playerName, position.world(),
                position.x(), position.y(), position.z(), position.yaw(), position.pitch(), updatedAt));
        dirty = true;
        prune(System.currentTimeMillis() / 1000L);
    }

    public synchronized OfflinePosition get(UUID playerId) {
        return positions.get(playerId.toString());
    }

    public synchronized UUID findPlayerId(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(nameOrUuid);
            return positions.containsKey(uuid.toString()) ? uuid : null;
        } catch (IllegalArgumentException ignored) {
        }
        String target = nameOrUuid.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, OfflinePosition> entry : positions.entrySet()) {
            OfflinePosition position = entry.getValue();
            if (position != null && position.playerName() != null
                    && position.playerName().toLowerCase(Locale.ROOT).equals(target)) {
                try {
                    return UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public synchronized List<KnownPlayer> knownPlayers() {
        List<KnownPlayer> result = new ArrayList<>();
        positions.entrySet().stream()
                .sorted(Map.Entry.<String, OfflinePosition>comparingByValue(
                        Comparator.comparingLong(OfflinePosition::updatedAt)).reversed())
                .limit(listLimit == 0 ? Long.MAX_VALUE : listLimit)
                .forEach(entry -> {
            String id = entry.getKey();
            OfflinePosition position = entry.getValue();
            try {
                if (position != null && position.playerName() != null && !position.playerName().isBlank()) {
                    result.add(new KnownPlayer(UUID.fromString(id), position.playerName()));
                }
            } catch (IllegalArgumentException ignored) {
            }
        });
        result.sort(java.util.Comparator.comparing(KnownPlayer::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public record KnownPlayer(UUID id, String name) {
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        try {
            writer.execute(this::drainSaves);
        } catch (RuntimeException exception) {
            saveScheduled = false;
            logger.error("Failed to schedule offline position save", exception);
        }
    }

    private boolean prune(long now) {
        boolean changed = false;
        if (retentionSeconds > 0) {
            changed |= positions.entrySet().removeIf(entry -> {
                OfflinePosition position = entry.getValue();
                return position == null || position.updatedAt() <= now - retentionSeconds;
            });
        }
        if (maxEntries > 0 && positions.size() > maxEntries) {
            positions.entrySet().stream()
                    .sorted(Map.Entry.<String, OfflinePosition>comparingByValue(
                            Comparator.comparingLong(OfflinePosition::updatedAt)))
                    .limit(positions.size() - maxEntries)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(positions::remove);
            changed = true;
        }
        return changed;
    }

    private void drainSaves() {
        while (true) {
            Map<String, OfflinePosition> snapshot;
            synchronized (this) {
                if (!dirty) {
                    saveScheduled = false;
                    return;
                }
                snapshot = new HashMap<>(positions);
                dirty = false;
            }
            try {
                AtomicFileWriter.write(file, gson.toJson(snapshot));
            } catch (Exception exception) {
                synchronized (this) {
                    dirty = true;
                    saveScheduled = false;
                }
                logger.error("Failed to save offline positions", exception);
                return;
            }
        }
    }
}
