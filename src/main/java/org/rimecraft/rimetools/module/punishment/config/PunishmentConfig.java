package org.rimecraft.rimetools.module.punishment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public record PunishmentConfig(boolean announcePunishments, int historyPageSize,
                               int muteNoticeCooldownSeconds) {
    private static final Logger LOGGER = LoggerFactory.getLogger("RIME Tools punishment module");
    public static final boolean DEFAULT_ANNOUNCE = true;
    public static final int DEFAULT_HISTORY_PAGE_SIZE = 10;
    public static final int DEFAULT_NOTICE_COOLDOWN = 3;

    public PunishmentConfig(boolean announcePunishments) {
        this(announcePunishments, DEFAULT_HISTORY_PAGE_SIZE, DEFAULT_NOTICE_COOLDOWN);
    }

    public static PunishmentConfig load(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Object loaded = new Yaml().load(reader);
            Map<String, Object> root = asMap(loaded);
            return new PunishmentConfig(
                    bool(root, "announce_punishments", DEFAULT_ANNOUNCE),
                    Math.clamp(integer(root, "history_page_size", DEFAULT_HISTORY_PAGE_SIZE), 5, 50),
                    Math.clamp(integer(root, "mute_notice_cooldown_seconds", DEFAULT_NOTICE_COOLDOWN), 0, 60));
        } catch (IOException | RuntimeException exception) {
            LOGGER.info("Punishment module configuration does not exist yet or could not be read; using defaults");
            return new PunishmentConfig(DEFAULT_ANNOUNCE, DEFAULT_HISTORY_PAGE_SIZE, DEFAULT_NOTICE_COOLDOWN);
        }
    }

    public void save(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("announce_punishments", announcePunishments);
        root.put("history_page_size", historyPageSize);
        root.put("mute_notice_cooldown_seconds", muteNoticeCooldownSeconds);
        try (Writer writer = Files.newBufferedWriter(path)) {
            new Yaml().dump(root, writer);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
