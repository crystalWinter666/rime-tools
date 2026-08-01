package org.rimecraft.rimetools.module.punishment.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import org.rimecraft.rimetools.util.AtomicFileWriter;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Versioned persistent moderation log with non-destructive revocation. */
public final class PunishmentRepository {
    public static final int FORMAT_VERSION = 2;
    private static final Type LEGACY_TYPE = new TypeToken<List<PunishmentRecord>>() { }.getType();
    private final Path file;
    private final Logger logger;
    private final Executor writer;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<PunishmentRecord> records = new ArrayList<>();
    private boolean dirty;
    private boolean saveScheduled;

    public PunishmentRepository(Path dataFolder, Logger logger) {
        this(dataFolder, logger, Runnable::run);
    }

    public PunishmentRepository(Path dataFolder, Logger logger, Executor writer) {
        file = dataFolder.resolve("punishments.json");
        this.logger = logger;
        this.writer = writer;
    }

    public synchronized boolean load() {
        try {
            List<PunishmentRecord> loaded = new ArrayList<>();
            boolean migrated = false;
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement root = gson.fromJson(json, JsonElement.class);
                if (root != null && root.isJsonArray()) {
                    List<PunishmentRecord> parsed = gson.fromJson(root, LEGACY_TYPE);
                    if (parsed != null) loaded.addAll(parsed);
                    migrated = true;
                } else if (root != null && root.isJsonObject()) {
                    Store store = gson.fromJson(root, Store.class);
                    if (store != null && store.records() != null) loaded.addAll(store.records());
                    migrated = store == null || store.version() != FORMAT_VERSION;
                }
            }
            records.clear();
            Set<UUID> ids = new HashSet<>();
            for (PunishmentRecord candidate : loaded) {
                if (candidate == null || candidate.playerId() == null || candidate.type() == null) continue;
                PunishmentRecord record = normalize(candidate);
                if (!ids.add(record.id())) {
                    record = withId(record, UUID.randomUUID());
                    ids.add(record.id());
                    migrated = true;
                }
                records.add(record);
            }
            dirty = migrated || loaded.stream().anyMatch(record -> record != null && record.id() == null);
            return true;
        } catch (Exception exception) {
            logger.error("Failed to load {}; keeping the current in-memory records", file, exception);
            return false;
        }
    }

    public synchronized void saveIfDirty() {
        if (dirty) scheduleSave();
    }

    public synchronized void add(PunishmentRecord record) {
        records.add(record);
        dirty = true;
    }

    public synchronized Optional<PunishmentRecord> find(UUID recordId) {
        return records.stream().filter(record -> record.id().equals(recordId)).findFirst();
    }

    public synchronized Optional<PunishmentRecord> activeBan(UUID playerId, long now) {
        return latestActive(playerId, now, PunishmentRecord.Type.TEMP_BAN, PunishmentRecord.Type.PERMA_BAN);
    }

    public synchronized Optional<PunishmentRecord> activeMute(UUID playerId, long now) {
        return latestActive(playerId, now, PunishmentRecord.Type.MUTE, PunishmentRecord.Type.PERMA_MUTE);
    }

    private Optional<PunishmentRecord> latestActive(UUID playerId, long now, PunishmentRecord.Type... types) {
        return records.stream()
                .filter(record -> record.playerId().equals(playerId) && record.isActive(now))
                .filter(record -> List.of(types).contains(record.type()))
                .max(Comparator.comparingInt(PunishmentRepository::permanentPriority)
                        .thenComparingLong(PunishmentRecord::issuedAt));
    }

    private static int permanentPriority(PunishmentRecord record) {
        return record.type() == PunishmentRecord.Type.PERMA_BAN
                || record.type() == PunishmentRecord.Type.PERMA_MUTE ? 1 : 0;
    }

    public synchronized List<PunishmentRecord> history(UUID playerId) {
        return records.stream().filter(record -> record.playerId().equals(playerId))
                .sorted(Comparator.comparingLong(PunishmentRecord::issuedAt).reversed()).toList();
    }

    public synchronized List<PunishmentRecord> allHistory() {
        return records.stream().sorted(Comparator.comparingLong(PunishmentRecord::issuedAt).reversed()).toList();
    }

    public synchronized int warningCount(UUID playerId) {
        return (int) records.stream().filter(record -> record.playerId().equals(playerId))
                .filter(record -> record.type() == PunishmentRecord.Type.WARN).count();
    }

    /** Compatibility operation: revokes every currently active record of a type. */
    public synchronized boolean revoke(UUID playerId, PunishmentRecord.Type type) {
        return revoke(playerId, type, System.currentTimeMillis() / 1000, "Console", null) > 0;
    }

    public synchronized int revoke(UUID playerId, PunishmentRecord.Type type, long now,
                                   String executor, String reason) {
        int changed = 0;
        for (int index = 0; index < records.size(); index++) {
            PunishmentRecord current = records.get(index);
            if (current.playerId().equals(playerId) && current.type() == type && current.isActive(now)) {
                records.set(index, current.revoke(now, executor, reason));
                changed++;
            }
        }
        dirty |= changed > 0;
        return changed;
    }

    public synchronized boolean revoke(UUID recordId, long now, String executor, String reason) {
        for (int index = 0; index < records.size(); index++) {
            PunishmentRecord current = records.get(index);
            if (current.id().equals(recordId) && current.isActive(now)) {
                records.set(index, current.revoke(now, executor, reason));
                dirty = true;
                return true;
            }
        }
        return false;
    }

    /** Kept for API compatibility; expiry is represented as derived state, preserving audit history. */
    public synchronized void pruneExpired(long now) {
        // Intentionally non-destructive in format version 2.
    }

    private static PunishmentRecord normalize(PunishmentRecord record) {
        if (record.id() != null) return record;
        return new PunishmentRecord(record.playerId(), record.playerName(), record.type(), record.issuedAt(),
                record.expiresAt(), record.reason(), record.executor());
    }

    private static PunishmentRecord withId(PunishmentRecord record, UUID id) {
        return new PunishmentRecord(id, record.playerId(), record.playerName(), record.type(),
                record.issuedAt(), record.expiresAt(), record.reason(), record.executor(),
                record.revokedAt(), record.revokedBy(), record.revokeReason());
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        try {
            writer.execute(this::drainSaves);
        } catch (RuntimeException exception) {
            saveScheduled = false;
            logger.error("Failed to schedule {} save", file, exception);
        }
    }

    private void drainSaves() {
        while (true) {
            Store snapshot;
            synchronized (this) {
                if (!dirty) {
                    saveScheduled = false;
                    return;
                }
                snapshot = new Store(FORMAT_VERSION, new ArrayList<>(records));
                dirty = false;
            }
            try {
                AtomicFileWriter.write(file, gson.toJson(snapshot));
            } catch (Exception exception) {
                synchronized (this) {
                    dirty = true;
                    saveScheduled = false;
                }
                logger.error("Failed to save {}", file, exception);
                return;
            }
        }
    }

    private record Store(int version, List<PunishmentRecord> records) { }
}
