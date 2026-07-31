package org.rimecraft.rimetools.module.teleport.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.teleport.model.Waypoint;
import org.rimecraft.rimetools.module.teleport.util.NameValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class StpImporter {
    private static final Map<String, String> VANILLA_DIMENSION_WORLD = Map.of(
            "minecraft:overworld", "world",
            "minecraft:the_nether", "world_nether",
            "minecraft:the_end", "world_the_end"
    );

    private StpImporter() {
    }

    public static Result load(MinecraftServer server, Path file, UuidMode uuidMode, boolean includeBack,
                              Map<String, String> uuidMap, Map<String, String> worldMap) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject personalRoot = object(root.get("personal_waypoints"));
        JsonObject globalRoot = object(root.get("global_waypoints"));
        JsonObject dimensionRoot = object(root.get("dimension_str2sid"));

        Map<Integer, String> dimensionsById = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : dimensionRoot.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                dimensionsById.put(entry.getValue().getAsInt(), entry.getKey());
            }
        }

        Map<String, String> uuidLookup = normalize(uuidMap);
        Map<String, String> worldLookup = normalize(worldMap);
        List<String> warnings = new ArrayList<>();
        Set<String> dimensions = new HashSet<>();
        Map<String, Map<String, Waypoint>> personal = new HashMap<>();
        Map<String, Waypoint> global = new HashMap<>();
        int players = 0;
        int personalCount = 0;
        int globalCount = 0;
        int skipped = 0;
        long now = Instant.now().getEpochSecond();

        for (Map.Entry<String, JsonElement> entry : personalRoot.entrySet()) {
            JsonObject waypoints = object(entry.getValue());
            if (waypoints.isEmpty()) continue;
            UUID owner = resolveUuid(server, entry.getKey(), uuidMode, uuidLookup, warnings);
            if (owner == null) {
                warnings.add("Invalid player key, skipped: " + entry.getKey());
                skipped++;
                continue;
            }
            Map<String, Waypoint> playerWaypoints = new HashMap<>();
            for (Map.Entry<String, JsonElement> waypointEntry : waypoints.entrySet()) {
                String name = waypointEntry.getKey();
                if (!includeBack && "__back__".equalsIgnoreCase(name)) {
                    skipped++;
                    continue;
                }
                Waypoint waypoint = build(name, array(waypointEntry.getValue()), owner, now,
                        dimensionsById, worldLookup, warnings, dimensions);
                if (waypoint == null) {
                    skipped++;
                    continue;
                }
                playerWaypoints.put(NameValidator.key(name), waypoint);
                personalCount++;
            }
            if (!playerWaypoints.isEmpty()) {
                personal.put(owner.toString(), playerWaypoints);
                players++;
            }
        }

        for (Map.Entry<String, JsonElement> entry : globalRoot.entrySet()) {
            Waypoint waypoint = build(entry.getKey(), array(entry.getValue()), null, now,
                    dimensionsById, worldLookup, warnings, dimensions);
            if (waypoint == null) {
                skipped++;
                continue;
            }
            global.put(NameValidator.key(entry.getKey()), waypoint);
            globalCount++;
        }

        return new Result(personal, global, players, personalCount, globalCount, skipped, warnings, dimensions);
    }

    public static Map<String, String> loadStringMap(Path file, List<String> warnings) {
        if (!Files.exists(file)) return Map.of();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> result = new HashMap<>();
            root.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) result.put(entry.getKey(), entry.getValue().getAsString());
            });
            return result;
        } catch (Exception exception) {
            warnings.add("Failed to load map file: " + file.getFileName());
            return Map.of();
        }
    }

    private static Waypoint build(String name, JsonArray location, UUID owner, long timestamp,
                                  Map<Integer, String> dimensionsById, Map<String, String> worldLookup,
                                  List<String> warnings, Set<String> dimensions) {
        if (location.size() < 4) {
            warnings.add("Invalid waypoint data: " + name);
            return null;
        }
        try {
            double x = location.get(0).getAsDouble();
            double y = location.get(1).getAsDouble();
            double z = location.get(2).getAsDouble();
            int dimensionId = location.get(3).getAsInt();
            float yaw = location.size() >= 6 ? location.get(4).getAsFloat() : 0.0f;
            float pitch = location.size() >= 6 ? location.get(5).getAsFloat() : 0.0f;
            String dimension = dimensionsById.get(dimensionId);
            if (dimension == null) {
                warnings.add("Missing dimension mapping for sid=" + dimensionId + ", waypoint=" + name);
                dimension = String.valueOf(dimensionId);
            } else {
                dimensions.add(dimension);
            }
            String world = worldLookup.getOrDefault(dimension,
                    worldLookup.getOrDefault(dimension.toLowerCase(Locale.ROOT),
                            VANILLA_DIMENSION_WORLD.getOrDefault(dimension, dimension)));
            return new Waypoint(name, world, x, y, z, yaw, pitch, "", owner, timestamp, timestamp);
        } catch (Exception exception) {
            warnings.add("Invalid location data for waypoint: " + name);
            return null;
        }
    }

    private static UUID resolveUuid(MinecraftServer server, String playerKey, UuidMode mode,
                                    Map<String, String> uuidLookup, List<String> warnings) {
        String key = playerKey == null ? "" : playerKey.trim();
        if (key.isEmpty()) return null;
        String mapped = uuidLookup.getOrDefault(key, uuidLookup.get(key.toLowerCase(Locale.ROOT)));
        if (mapped != null) {
            UUID parsed = parseUuid(mapped);
            if (parsed != null) return parsed;
            warnings.add("Invalid UUID mapping for player: " + key);
        }
        return switch (mode) {
            case RAW -> parseUuid(key);
            case OFFLINE -> offlineUuid(key);
            case AUTO, BUKKIT -> {
                UUID parsed = parseUuid(key);
                if (parsed != null) yield parsed;
                ServerPlayer online = server.getPlayerList().getPlayerByName(key);
                yield online == null ? offlineUuid(key) : online.getUUID();
            }
        };
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> normalize(Map<String, String> input) {
        Map<String, String> result = new HashMap<>();
        if (input == null) return result;
        input.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
                result.put(key.toLowerCase(Locale.ROOT), value);
            }
        });
        return result;
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    public enum UuidMode {BUKKIT, OFFLINE, RAW, AUTO}

    public record Result(Map<String, Map<String, Waypoint>> personal, Map<String, Waypoint> global,
                         int personalPlayers, int personalWaypoints, int globalWaypoints, int skipped,
                         List<String> warnings, Set<String> dimensions) {
    }
}
