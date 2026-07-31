package org.rimecraft.rimetools.module.teleport.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TeleportConfig {
    public final List<String> worlds;
    public final boolean easyTp;
    public final int waypointNameMaxLength;
    public final boolean allowUnicodeNames;
    public final int personalMaxWaypoints;
    public final int globalMaxWaypoints;
    public final int saveIntervalSeconds;
    public final int offlinePlayerRetentionDays;
    public final int offlinePlayerMaxEntries;
    public final int offlinePlayerListLimit;
    public final boolean backOnDeath;
    public final int tpaTimeoutSeconds;
    public final DuplicatePolicy tpaDuplicatePolicy;
    public final int confirmTimeoutSeconds;
    public final String defaultLocale;
    public final CooldownConfig cooldown;
    public final CostConfig cost;
    public final SafetyConfig safety;
    public final RandomTeleportConfig randomTeleport;
    public final CommandsConfig commands;

    private TeleportConfig(Map<String, Object> root) {
        worlds = stringList(root.get("worlds"));
        easyTp = bool(root, "easy_tp", true);
        waypointNameMaxLength = integer(root, "waypoint_name_max_length", 24);
        allowUnicodeNames = bool(root, "allow_unicode_names", false);
        personalMaxWaypoints = integer(root, "personal_max_waypoints", 10);
        globalMaxWaypoints = integer(root, "global_max_waypoints", 100);
        saveIntervalSeconds = integer(root, "save_interval_seconds", 120);
        offlinePlayerRetentionDays = integer(root, "offline_player_retention_days", 180);
        offlinePlayerMaxEntries = integer(root, "offline_player_max_entries", 5000);
        offlinePlayerListLimit = integer(root, "offline_player_list_limit", 1000);
        backOnDeath = bool(root, "back_on_death", true);
        tpaTimeoutSeconds = integer(root, "tpa_timeout_seconds", 60);
        tpaDuplicatePolicy = enumValue(DuplicatePolicy.class, string(root, "tpa_duplicate_policy", "REJECT"), DuplicatePolicy.REJECT);
        confirmTimeoutSeconds = Math.clamp(integer(root, "confirm_timeout_seconds", 15), 5, 3600);
        defaultLocale = string(root, "default_locale", "en_US");

        Map<String, Object> cooldownMap = section(root, "cooldown");
        cooldown = new CooldownConfig(
                integer(cooldownMap, "waypoint", 0),
                integer(cooldownMap, "tp", 0),
                integer(cooldownMap, "back", 0),
                integer(cooldownMap, "rtp", 60)
        );

        Map<String, Object> costMap = section(root, "cost");
        Map<String, Object> expMap = section(costMap, "exp");
        Map<String, Object> itemMap = section(costMap, "item");
        Map<String, Object> crossworldMap = section(costMap, "crossworld");
        cost = new CostConfig(
                new ExpCost(bool(expMap, "enabled", false), integer(expMap, "base", 0), decimal(expMap, "per_block", 0.0)),
                new ItemCost(bool(itemMap, "enabled", false), normalizeItemId(string(itemMap, "material", "DIAMOND")),
                        integer(itemMap, "custom_model_data", -1), integer(itemMap, "base", 0), decimal(itemMap, "per_block", 0.0)),
                new CrossworldCost(enumValue(CrossworldMode.class, string(crossworldMap, "mode", "FIXED_DISTANCE"), CrossworldMode.FIXED_DISTANCE),
                        decimal(crossworldMap, "distance", 1000.0), integer(crossworldMap, "extra_cost", 0)),
                enumValue(Rounding.class, string(costMap, "rounding", "CEIL"), Rounding.CEIL)
        );

        Map<String, Object> safetyMap = section(root, "safety_check");
        safety = new SafetyConfig(
                bool(safetyMap, "enabled", true),
                enumValue(SafetyMode.class, string(safetyMap, "mode", "CONFIRM"), SafetyMode.CONFIRM),
                integer(safetyMap, "safety_search_radius", 8),
                integer(safetyMap, "safety_search_vertical", 3),
                enumValue(NearbyFallback.class, string(safetyMap, "nearby_fallback", "CONFIRM"), NearbyFallback.CONFIRM),
                bool(safetyMap, "disallow_water", true),
                integer(safetyMap, "min_y", -64)
        );

        Map<String, Object> randomTeleportMap = section(root, "random_teleport");
        randomTeleport = new RandomTeleportConfig(
                bool(randomTeleportMap, "enabled", true),
                integer(randomTeleportMap, "min_radius", 500),
                integer(randomTeleportMap, "max_radius", 5000),
                integer(randomTeleportMap, "max_attempts", 8),
                integer(randomTeleportMap, "max_concurrent_searches", 2)
        );

        Map<String, Object> commandsMap = section(root, "commands");
        commands = new CommandsConfig(bool(commandsMap, "override_tp", false));
    }

    public static TeleportConfig load(Path configFile) throws IOException {
        copyDefault("teleport.yml", configFile);
        try (InputStream input = Files.newInputStream(configFile)) {
            Object loaded = new Yaml().load(input);
            return new TeleportConfig(asMap(loaded));
        }
    }

    public static String normalizeWorld(String world) {
        if (world == null) {
            return "";
        }
        return switch (world.toLowerCase(Locale.ROOT)) {
            case "minecraft:overworld", "overworld" -> "world";
            case "minecraft:the_nether", "the_nether", "nether" -> "world_nether";
            case "minecraft:the_end", "the_end", "end" -> "world_the_end";
            default -> world;
        };
    }

    public static void copyDefault(String resourceName, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = TeleportConfig.class.getResourceAsStream("/teleport/" + resourceName)) {
            if (input == null) {
                throw new IOException("Missing bundled resource: " + resourceName);
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizeItemId(String raw) {
        String value = raw == null ? "diamond" : raw.trim().toLowerCase(Locale.ROOT);
        return value.contains(":") ? value : "minecraft:" + value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> section(Map<String, Object> root, String key) {
        return asMap(root.get(key));
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return List.copyOf(result);
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double decimal(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public boolean isWorldAllowed(String worldName) {
        if (worlds.isEmpty()) {
            return true;
        }
        String normalized = normalizeWorld(worldName);
        return worlds.stream().map(TeleportConfig::normalizeWorld).anyMatch(normalized::equalsIgnoreCase);
    }

    public enum DuplicatePolicy {REJECT, REPLACE}

    public enum CrossworldMode {FIXED_DISTANCE, EXTRA_COST}

    public enum Rounding {CEIL, FLOOR, ROUND}

    public enum SafetyMode {CONFIRM, NEARBY_SAFE}

    public enum NearbyFallback {CONFIRM, DENY}

    public record CooldownConfig(int waypointSeconds, int tpSeconds, int backSeconds, int rtpSeconds) {
    }

    public record ExpCost(boolean enabled, int base, double perBlock) {
    }

    public record ItemCost(boolean enabled, String itemId, int customModelData, int base, double perBlock) {
    }

    public record CrossworldCost(CrossworldMode mode, double distance, int extraCost) {
    }

    public record CostConfig(ExpCost exp, ItemCost item, CrossworldCost crossworld, Rounding rounding) {
    }

    public record SafetyConfig(boolean enabled, SafetyMode mode, int searchRadius, int searchVertical,
                               NearbyFallback nearbyFallback, boolean disallowWater, int minY) {
    }

    public record RandomTeleportConfig(boolean enabled, int minRadius, int maxRadius, int maxAttempts,
                                       int maxConcurrentSearches) {
        public RandomTeleportConfig {
            minRadius = Math.max(0, minRadius);
            maxRadius = Math.max(minRadius + 16, maxRadius);
            maxAttempts = Math.clamp(maxAttempts, 1, 32);
            maxConcurrentSearches = Math.clamp(maxConcurrentSearches, 1, 16);
        }
    }

    public record CommandsConfig(boolean overrideTp) {
    }
}
