package org.rimecraft.rimetools.module.punishment.data;

import org.rimecraft.rimetools.util.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Persistent violation log. Ban and mute state is derived from the latest
 * matching active record, so punishments survive restarts.
 */
public final class PunishmentRepository {
    private static final Type TYPE = new TypeToken<List<PunishmentRecord>>() {
    }.getType();
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
            if (Files.exists(file)) {
                List<PunishmentRecord> parsed = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
                if (parsed != null) loaded.addAll(parsed);
            }
            records.clear();
            records.addAll(loaded);
            dirty = false;
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

    /** Latest active ban for the player, if any. */
    public synchronized Optional<PunishmentRecord> activeBan(UUID playerId, long now) {
        return records.stream()
                .filter(record -> record.playerId().equals(playerId))
                .filter(record -> record.type() == PunishmentRecord.Type.TEMP_BAN
                        || record.type() == PunishmentRecord.Type.PERMA_BAN)
                .filter(record -> record.isActive(now))
                .max(Comparator.comparingLong(PunishmentRecord::issuedAt));
    }

    /** Latest active mute for the player, if any. */
    public synchronized Optional<PunishmentRecord> activeMute(UUID playerId, long now) {
        return records.stream()
                .filter(record -> record.playerId().equals(playerId))
                .filter(record -> record.type() == PunishmentRecord.Type.MUTE)
                .filter(record -> record.isActive(now))
                .max(Comparator.comparingLong(PunishmentRecord::issuedAt));
    }

    /** All recorded actions for the player, newest first. */
    public synchronized List<PunishmentRecord> history(UUID playerId) {
        return records.stream()
                .filter(record -> record.playerId().equals(playerId))
                .sorted(Comparator.comparingLong(PunishmentRecord::issuedAt).reversed())
                .toList();
    }

    /** Removes the active ban or mute so the player is unblocked again. */
    public synchronized boolean revoke(UUID playerId, PunishmentRecord.Type type) {
        boolean removed = records.removeIf(record ->
                record.playerId().equals(playerId)
                        && record.type() == type
                        && record.isActive(Long.MAX_VALUE));
        dirty |= removed;
        return removed;
    }

    /** Drops expired ban/mute records so they no longer accumulate. */
    public synchronized void pruneExpired(long now) {
        boolean removed = records.removeIf(record ->
                (record.type() == PunishmentRecord.Type.TEMP_BAN || record.type() == PunishmentRecord.Type.MUTE)
                        && !record.isActive(now));
        dirty |= removed;
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
            List<PunishmentRecord> snapshot;
            synchronized (this) {
                if (!dirty) {
                    saveScheduled = false;
                    return;
                }
                snapshot = new ArrayList<>(records);
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
}
