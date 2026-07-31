package org.rimecraft.rimetools.module.teleport.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.rimecraft.rimetools.module.teleport.model.Waypoint;
import org.rimecraft.rimetools.module.teleport.util.NameValidator;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;

public final class WaypointRepository {
    private static final Type PERSONAL_TYPE = new TypeToken<Map<String, Map<String, Waypoint>>>() {
    }.getType();
    private static final Type GLOBAL_TYPE = new TypeToken<Map<String, Waypoint>>() {
    }.getType();

    private final Path personalFile;
    private final Path globalFile;
    private final Logger logger;
    private final Executor writer;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Map<String, Waypoint>> personal = new HashMap<>();
    private final Map<String, Waypoint> global = new HashMap<>();
    private boolean dirty;
    private boolean saveScheduled;

    public WaypointRepository(Path dataFolder, Logger logger) {
        this(dataFolder, logger, Runnable::run);
    }

    public WaypointRepository(Path dataFolder, Logger logger, Executor writer) {
        personalFile = dataFolder.resolve("personal_waypoints.json");
        globalFile = dataFolder.resolve("global_waypoints.json");
        this.logger = logger;
        this.writer = writer;
    }

    private static Waypoint copy(Waypoint waypoint) {
        return new Waypoint(waypoint.getName(), waypoint.getWorld(), waypoint.getX(), waypoint.getY(),
                waypoint.getZ(), waypoint.getYaw(), waypoint.getPitch(), waypoint.getAlias(),
                waypoint.getDescription(), waypoint.getOwner(), waypoint.getCreatedAt(), waypoint.getUpdatedAt());
    }

    public synchronized boolean load() {
        try {
            Map<String, Map<String, Waypoint>> loadedPersonal = read(personalFile, PERSONAL_TYPE);
            Map<String, Waypoint> loadedGlobal = read(globalFile, GLOBAL_TYPE);

            personal.clear();
            loadedPersonal.forEach((playerId, points) -> {
                if (points != null) {
                    Map<String, Waypoint> validPoints = new HashMap<>();
                    points.forEach((name, waypoint) -> {
                        if (name != null && waypoint != null) validPoints.put(name, waypoint);
                    });
                    personal.put(playerId, validPoints);
                }
            });
            global.clear();
            loadedGlobal.forEach((name, waypoint) -> {
                if (name != null && waypoint != null) global.put(name, waypoint);
            });
            dirty = false;
            return true;
        } catch (Exception exception) {
            logger.error("Failed to load waypoint data; keeping the current in-memory data", exception);
            return false;
        }
    }

    private <T> T read(Path file, Type type) throws Exception {
        if (!Files.exists(file)) {
            return gson.fromJson("{}", type);
        }
        T value = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        if (value == null) {
            throw new IllegalStateException("Waypoint file is empty or contains JSON null: " + file);
        }
        return value;
    }

    public synchronized void saveIfDirty() {
        if (dirty) scheduleSave();
    }

    public synchronized void save() {
        dirty = true;
        scheduleSave();
    }

    public synchronized void replaceAll(Map<String, Map<String, Waypoint>> personalData, Map<String, Waypoint> globalData) {
        personal.clear();
        global.clear();
        if (personalData != null) personal.putAll(personalData);
        if (globalData != null) global.putAll(globalData);
        dirty = true;
    }

    public synchronized Waypoint getPersonal(UUID uuid, String name) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        return map == null ? null : map.get(NameValidator.key(name));
    }

    public synchronized Waypoint getGlobal(String name) {
        return global.get(NameValidator.key(name));
    }

    public synchronized boolean setPersonal(UUID uuid, Waypoint waypoint) {
        Map<String, Waypoint> map = personal.computeIfAbsent(uuid.toString(), ignored -> new HashMap<>());
        boolean existed = map.put(NameValidator.key(waypoint.getName()), waypoint) != null;
        dirty = true;
        return existed;
    }

    public synchronized boolean setGlobal(Waypoint waypoint) {
        boolean existed = global.put(NameValidator.key(waypoint.getName()), waypoint) != null;
        dirty = true;
        return existed;
    }

    public synchronized boolean deletePersonal(UUID uuid, String name) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        if (map == null || map.remove(NameValidator.key(name)) == null) return false;
        if (map.isEmpty()) personal.remove(uuid.toString());
        dirty = true;
        return true;
    }

    public synchronized boolean deleteGlobal(String name) {
        if (global.remove(NameValidator.key(name)) == null) return false;
        dirty = true;
        return true;
    }

    public synchronized boolean deleteGlobalOwner(UUID caller, String name) {
        Waypoint waypoint = global.get(NameValidator.key(name));
        if (waypoint == null) return false;
        if (waypoint.getOwner() != null && !waypoint.getOwner().equals(caller)) return false;
        global.remove(NameValidator.key(name));
        dirty = true;
        return true;
    }

    public synchronized List<Waypoint> listPersonal(UUID uuid) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        return map == null ? Collections.emptyList() : new ArrayList<>(map.values());
    }

    public synchronized List<Waypoint> listGlobal() {
        return new ArrayList<>(global.values());
    }

    public synchronized int countPersonal(UUID uuid) {
        Map<String, Waypoint> map = personal.get(uuid.toString());
        return map == null ? 0 : map.size();
    }

    public synchronized int countGlobal() {
        return global.size();
    }

    public synchronized int countPersonal() {
        return personal.values().stream().mapToInt(Map::size).sum();
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        try {
            writer.execute(this::drainSaves);
        } catch (RuntimeException exception) {
            saveScheduled = false;
            logger.error("Failed to schedule waypoint data save", exception);
        }
    }

    private void drainSaves() {
        while (true) {
            Snapshot snapshot;
            synchronized (this) {
                if (!dirty) {
                    saveScheduled = false;
                    return;
                }
                snapshot = snapshot();
                dirty = false;
            }
            try {
                AtomicFileWriter.write(personalFile, gson.toJson(snapshot.personal));
                AtomicFileWriter.write(globalFile, gson.toJson(snapshot.global));
            } catch (Exception exception) {
                synchronized (this) {
                    dirty = true;
                    saveScheduled = false;
                }
                logger.error("Failed to save waypoint data", exception);
                return;
            }
        }
    }

    private Snapshot snapshot() {
        Map<String, Map<String, Waypoint>> personalCopy = new HashMap<>();
        personal.forEach((player, points) -> {
            Map<String, Waypoint> pointsCopy = new HashMap<>();
            points.forEach((name, waypoint) -> pointsCopy.put(name, copy(waypoint)));
            personalCopy.put(player, pointsCopy);
        });
        Map<String, Waypoint> globalCopy = new HashMap<>();
        global.forEach((name, waypoint) -> globalCopy.put(name, copy(waypoint)));
        return new Snapshot(personalCopy, globalCopy);
    }

    private record Snapshot(Map<String, Map<String, Waypoint>> personal, Map<String, Waypoint> global) {
    }
}
