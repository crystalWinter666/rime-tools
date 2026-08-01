package org.rimecraft.rimetools.module.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Validated chat governance configuration. Missing v1 fields are migrated with defaults. */
public record ChatConfig(boolean antiSpamEnabled, int windowSeconds, int maxMessages,
                         AntiSpamAction action, int muteSeconds,
                         boolean duplicateEnabled, int duplicateWindowSeconds, int maxDuplicates,
                         double similarityThreshold, int maxMessageLength, boolean stripFormatting,
                         int stateRetentionSeconds, List<String> mutedCommands, List<String> allowedCommands) {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_WINDOW_SECONDS = 5;
    public static final int DEFAULT_MAX_MESSAGES = 6;
    public static final AntiSpamAction DEFAULT_ACTION = AntiSpamAction.MUTE;
    public static final int DEFAULT_MUTE_SECONDS = 60;
    public static final List<String> DEFAULT_MUTED_COMMANDS = List.of("msg", "tell", "w", "teammsg", "tm");
    public static final List<String> DEFAULT_ALLOWED_COMMANDS = List.of("punish", "help");
    private static final Logger LOGGER = LoggerFactory.getLogger("RIME Tools chat module");

    /** Compatibility constructor for existing callers/tests. */
    public ChatConfig(boolean enabled, int window, int max, AntiSpamAction action, int mute) {
        this(enabled, window, max, action, mute, true, 20, 2, 0.9, 256,
                true, 600, DEFAULT_MUTED_COMMANDS, DEFAULT_ALLOWED_COMMANDS);
    }

    public ChatConfig {
        mutedCommands = List.copyOf(mutedCommands);
        allowedCommands = List.copyOf(allowedCommands);
    }

    public static ChatConfig load(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Object> root = asMap(new Yaml().load(reader));
            Map<String, Object> anti = section(root, "anti_spam");
            Map<String, Object> duplicate = section(anti, "duplicate_detection");
            Map<String, Object> message = section(root, "message_rules");
            Map<String, Object> mute = section(root, "mute_interception");
            return new ChatConfig(
                    bool(anti, "enabled", DEFAULT_ENABLED),
                    clamp(integer(anti, "window_seconds", DEFAULT_WINDOW_SECONDS), 1, 300),
                    clamp(integer(anti, "max_messages", DEFAULT_MAX_MESSAGES), 1, 100),
                    enumeration(AntiSpamAction.class, string(anti, "action", null), DEFAULT_ACTION),
                    clamp(integer(anti, "mute_seconds", DEFAULT_MUTE_SECONDS), 1, 31_557_600),
                    bool(duplicate, "enabled", true),
                    clamp(integer(duplicate, "window_seconds", 20), 1, 600),
                    clamp(integer(duplicate, "max_duplicates", 2), 1, 20),
                    clamp(decimal(duplicate, "similarity_threshold", 0.9), 0.5, 1.0),
                    clamp(integer(message, "max_length", 256), 1, 2048),
                    bool(message, "strip_formatting", true),
                    clamp(integer(anti, "state_retention_seconds", 600), 60, 86_400),
                    strings(mute, "blocked_commands", DEFAULT_MUTED_COMMANDS),
                    strings(mute, "allowed_commands", DEFAULT_ALLOWED_COMMANDS));
        } catch (IOException | RuntimeException exception) {
            LOGGER.info("Chat module configuration does not exist yet or could not be read; using defaults");
            return defaults();
        }
    }

    public static ChatConfig defaults() {
        return new ChatConfig(DEFAULT_ENABLED, DEFAULT_WINDOW_SECONDS, DEFAULT_MAX_MESSAGES,
                DEFAULT_ACTION, DEFAULT_MUTE_SECONDS);
    }

    public void save(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> anti = new LinkedHashMap<>();
        anti.put("enabled", antiSpamEnabled);
        anti.put("window_seconds", windowSeconds);
        anti.put("max_messages", maxMessages);
        anti.put("action", action.name());
        anti.put("mute_seconds", muteSeconds);
        anti.put("state_retention_seconds", stateRetentionSeconds);
        Map<String, Object> duplicate = new LinkedHashMap<>();
        duplicate.put("enabled", duplicateEnabled);
        duplicate.put("window_seconds", duplicateWindowSeconds);
        duplicate.put("max_duplicates", maxDuplicates);
        duplicate.put("similarity_threshold", similarityThreshold);
        anti.put("duplicate_detection", duplicate);
        root.put("anti_spam", anti);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("max_length", maxMessageLength);
        message.put("strip_formatting", stripFormatting);
        root.put("message_rules", message);
        Map<String, Object> mute = new LinkedHashMap<>();
        mute.put("blocked_commands", mutedCommands);
        mute.put("allowed_commands", allowedCommands);
        root.put("mute_interception", mute);
        try (Writer writer = Files.newBufferedWriter(path)) { new Yaml().dump(root, writer); }
    }

    public boolean blockedCommunicationCommand(String command) {
        String root = commandRoot(command);
        return mutedCommands.stream().anyMatch(value -> value.equalsIgnoreCase(root));
    }

    public boolean allowedWhileMuted(String command) {
        String root = commandRoot(command);
        return allowedCommands.stream().anyMatch(value -> value.equalsIgnoreCase(root));
    }

    private static String commandRoot(String command) {
        String value = command == null ? "" : command.stripLeading();
        if (value.startsWith("/")) value = value.substring(1);
        int space = value.indexOf(' ');
        String root = (space < 0 ? value : value.substring(0, space)).toLowerCase(Locale.ROOT);
        int namespace = root.indexOf(':');
        return namespace < 0 ? root : root.substring(namespace + 1);
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    private static Map<String, Object> section(Map<String, Object> map, String key) { return asMap(map.get(key)); }
    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key); return value == null ? fallback : String.valueOf(value).trim();
    }
    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key); return value == null ? fallback
                : value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value).trim());
    }
    private static int integer(Map<String, Object> map, String key, int fallback) {
        try { Object value = map.get(key); return value == null ? fallback
                : value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim()); }
        catch (NumberFormatException exception) { return fallback; }
    }
    private static double decimal(Map<String, Object> map, String key, double fallback) {
        try { Object value = map.get(key); return value == null ? fallback
                : value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value).trim()); }
        catch (NumberFormatException exception) { return fallback; }
    }
    private static List<String> strings(Map<String, Object> map, String key, List<String> fallback) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) return fallback;
        List<String> result = new ArrayList<>();
        list.stream().map(String::valueOf).map(String::trim).filter(item -> item.matches("[a-zA-Z0-9:_-]{1,64}"))
                .map(item -> item.toLowerCase(Locale.ROOT)).distinct().limit(64).forEach(result::add);
        return result.isEmpty() ? fallback : result;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static <T extends Enum<T>> T enumeration(Class<T> type, String value, T fallback) {
        try { return value == null ? fallback : Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return fallback; }
    }

    public enum AntiSpamAction {WARN, MUTE, KICK}
}
