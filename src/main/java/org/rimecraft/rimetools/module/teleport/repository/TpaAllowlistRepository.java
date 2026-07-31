package org.rimecraft.rimetools.module.teleport.repository;

import org.rimecraft.rimetools.util.AtomicFileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;

public final class TpaAllowlistRepository {
    private static final Type TYPE = new TypeToken<Map<String, Set<String>>>() {
    }.getType();
    private final Path file;
    private final Logger logger;
    private final Executor writer;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Set<String>> allowlist = new HashMap<>();
    private boolean dirty;
    private boolean saveScheduled;

    public TpaAllowlistRepository(Path dataFolder, Logger logger) {
        this(dataFolder, logger, Runnable::run);
    }

    public TpaAllowlistRepository(Path dataFolder, Logger logger, Executor writer) {
        file = dataFolder.resolve("tpa_allowlist.json");
        this.logger = logger;
        this.writer = writer;
    }

    public synchronized boolean load() {
        try {
            Map<String, Set<String>> loaded = new HashMap<>();
            if (Files.exists(file)) {
                loaded = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
                if (loaded == null)
                    throw new IllegalStateException("Allowlist file is empty or contains JSON null: " + file);
            }
            Map<String, Set<String>> replacement = new HashMap<>();
            loaded.forEach((key, value) -> replacement.put(key, value == null ? new HashSet<>() : new HashSet<>(value)));
            allowlist.clear();
            allowlist.putAll(replacement);
            dirty = false;
            return true;
        } catch (Exception exception) {
            logger.error("Failed to load {}; keeping the current in-memory allowlist", file, exception);
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

    public synchronized boolean isAllowed(UUID target, UUID sender) {
        Set<String> values = allowlist.get(target.toString());
        return values != null && values.contains(sender.toString());
    }

    public synchronized boolean add(UUID target, UUID allowed) {
        boolean added = allowlist.computeIfAbsent(target.toString(), ignored -> new HashSet<>()).add(allowed.toString());
        dirty |= added;
        return added;
    }

    public synchronized boolean remove(UUID target, UUID allowed) {
        Set<String> values = allowlist.get(target.toString());
        if (values == null || !values.remove(allowed.toString())) return false;
        if (values.isEmpty()) allowlist.remove(target.toString());
        dirty = true;
        return true;
    }

    public synchronized List<UUID> list(UUID target) {
        Set<String> values = allowlist.get(target.toString());
        if (values == null) return List.of();
        List<UUID> result = new ArrayList<>();
        for (String value : values) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
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
            Map<String, Set<String>> snapshot;
            synchronized (this) {
                if (!dirty) {
                    saveScheduled = false;
                    return;
                }
                snapshot = new HashMap<>();
                allowlist.forEach((player, allowed) -> snapshot.put(player, new HashSet<>(allowed)));
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
