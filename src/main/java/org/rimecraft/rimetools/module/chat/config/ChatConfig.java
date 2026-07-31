package org.rimecraft.rimetools.module.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ChatConfig(boolean antiSpamEnabled, int windowSeconds, int maxMessages,
                         AntiSpamAction action, int muteSeconds) {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_WINDOW_SECONDS = 5;
    public static final int DEFAULT_MAX_MESSAGES = 6;
    public static final AntiSpamAction DEFAULT_ACTION = AntiSpamAction.MUTE;
    public static final int DEFAULT_MUTE_SECONDS = 60;
    private static final Logger LOGGER = LoggerFactory.getLogger("RIME Tools chat module");

    public static ChatConfig load(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Object loaded = new Yaml().load(reader);
            Map<String, Object> root = asMap(loaded);
            Map<String, Object> antiSpam = section(root, "anti_spam");
            return new ChatConfig(
                    bool(antiSpam, "enabled", DEFAULT_ENABLED),
                    Math.max(1, intValue(antiSpam, "window_seconds", DEFAULT_WINDOW_SECONDS)),
                    Math.max(1, intValue(antiSpam, "max_messages", DEFAULT_MAX_MESSAGES)),
                    enumValue(AntiSpamAction.class, string(antiSpam, "action", null), DEFAULT_ACTION),
                    Math.max(0, intValue(antiSpam, "mute_seconds", DEFAULT_MUTE_SECONDS)));
        } catch (IOException | RuntimeException exception) {
            LOGGER.info("Chat module configuration does not exist yet or could not be read; using defaults");
            return new ChatConfig(DEFAULT_ENABLED, DEFAULT_WINDOW_SECONDS, DEFAULT_MAX_MESSAGES,
                    DEFAULT_ACTION, DEFAULT_MUTE_SECONDS);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> section(Map<String, Object> root, String key) {
        return asMap(root.get(key));
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static int intValue(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    public void save(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> antiSpam = new LinkedHashMap<>();
        antiSpam.put("enabled", antiSpamEnabled);
        antiSpam.put("window_seconds", windowSeconds);
        antiSpam.put("max_messages", maxMessages);
        antiSpam.put("action", action.name());
        antiSpam.put("mute_seconds", muteSeconds);
        root.put("anti_spam", antiSpam);
        try (Writer writer = Files.newBufferedWriter(path)) {
            new Yaml().dump(root, writer);
        }
    }

    public enum AntiSpamAction {MUTE, KICK}
}
