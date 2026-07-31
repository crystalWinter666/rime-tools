package org.rimecraft.rimetools.module.title.config;

import org.rimecraft.rimetools.module.title.title.TitleInputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public record TitleConfig(String defaultTitle, String defaultColor,
                          WeeklyRankAwards weeklyRankAwards, MonthlyRankAwards monthlyRankAwards) {
    public static final String DEFAULT_TITLE = "玩家";
    public static final String DEFAULT_COLOR = "#AAAAAA";
    public static final WeeklyRankAwards DEFAULT_WEEKLY_RANK_AWARDS = new WeeklyRankAwards(
            true, DayOfWeek.MONDAY, LocalTime.of(0, 5), ZoneId.of("Asia/Shanghai"));
    public static final MonthlyRankAwards DEFAULT_MONTHLY_RANK_AWARDS = new MonthlyRankAwards(
            true, 1, LocalTime.of(0, 5), ZoneId.of("Asia/Shanghai"));
    private static final Logger LOGGER = LoggerFactory.getLogger("RIME Tools title module");

    public TitleConfig {
        if (!TitleInputValidator.isValidDisplayName(defaultTitle)) {
            LOGGER.warn("Invalid default-title in RIME Tools title module configuration; using the safe default");
            defaultTitle = DEFAULT_TITLE;
        }
        var normalizedColor = TitleInputValidator.normalizeColor(defaultColor);
        if (normalizedColor.isEmpty()) {
            LOGGER.warn("Invalid default-color in RIME Tools title module configuration; using the safe default");
        }
        defaultColor = normalizedColor.orElse(DEFAULT_COLOR);
        if (weeklyRankAwards == null) weeklyRankAwards = DEFAULT_WEEKLY_RANK_AWARDS;
        if (monthlyRankAwards == null) monthlyRankAwards = DEFAULT_MONTHLY_RANK_AWARDS;
    }

    public TitleConfig(String defaultTitle, String defaultColor) {
        this(defaultTitle, defaultColor, DEFAULT_WEEKLY_RANK_AWARDS, DEFAULT_MONTHLY_RANK_AWARDS);
    }

    public static TitleConfig load(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Object loaded = new Yaml().load(reader);
            Map<String, Object> root = asMap(loaded);
            Map<String, Object> weekly = section(root, "weekly-rank-awards");
            Map<String, Object> monthly = section(root, "monthly-rank-awards");
            return new TitleConfig(
                    string(root, "default-title", DEFAULT_TITLE),
                    string(root, "default-color", DEFAULT_COLOR),
                    new WeeklyRankAwards(
                            bool(weekly, "enabled", true),
                            enumValue(DayOfWeek.class, string(weekly, "day", null), DayOfWeek.MONDAY),
                            timeValue(string(weekly, "time", null), LocalTime.of(0, 5)),
                            zoneValue(string(weekly, "zone", null), ZoneId.of("Asia/Shanghai"))),
                    new MonthlyRankAwards(
                            bool(monthly, "enabled", true),
                            intValue(monthly, "day", 1),
                            timeValue(string(monthly, "time", null), LocalTime.of(0, 5)),
                            zoneValue(string(monthly, "zone", null), ZoneId.of("Asia/Shanghai"))));
        } catch (IOException | RuntimeException exception) {
            LOGGER.info("RIME Tools title module configuration does not exist yet or could not be read; using defaults");
            return new TitleConfig(DEFAULT_TITLE, DEFAULT_COLOR, DEFAULT_WEEKLY_RANK_AWARDS, DEFAULT_MONTHLY_RANK_AWARDS);
        }
    }

    /**
     * 读取旧版 {@code title.properties}（扁平 key=value）并转换为 YAML 版配置，
     * 供从旧版本升级时迁移使用。
     */
    public static TitleConfig fromProperties(Path path) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.info("Legacy title module configuration could not be read; using defaults");
            return new TitleConfig(DEFAULT_TITLE, DEFAULT_COLOR, DEFAULT_WEEKLY_RANK_AWARDS, DEFAULT_MONTHLY_RANK_AWARDS);
        }
        return new TitleConfig(
                properties.getProperty("default-title", DEFAULT_TITLE),
                properties.getProperty("default-color", DEFAULT_COLOR),
                new WeeklyRankAwards(
                        booleanValue(properties, "weekly-rank-awards-enabled", true),
                        enumValue(DayOfWeek.class, properties.getProperty("weekly-rank-awards-day"), DayOfWeek.MONDAY),
                        timeValue(properties.getProperty("weekly-rank-awards-time"), LocalTime.of(0, 5)),
                        zoneValue(properties.getProperty("weekly-rank-awards-zone"), ZoneId.of("Asia/Shanghai"))),
                new MonthlyRankAwards(
                        booleanValue(properties, "monthly-rank-awards-enabled", true),
                        intValue(properties, "monthly-rank-awards-day", 1),
                        timeValue(properties.getProperty("monthly-rank-awards-time"), LocalTime.of(0, 5)),
                        zoneValue(properties.getProperty("monthly-rank-awards-zone"), ZoneId.of("Asia/Shanghai"))));
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

    private static boolean booleanValue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int intValue(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
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

    private static LocalTime timeValue(String value, LocalTime fallback) {
        try {
            return value == null ? fallback : LocalTime.parse(value.trim());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static ZoneId zoneValue(String value, ZoneId fallback) {
        try {
            return value == null ? fallback : ZoneId.of(value.trim());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    public void save(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("default-title", defaultTitle);
        root.put("default-color", defaultColor);

        Map<String, Object> weekly = new LinkedHashMap<>();
        weekly.put("enabled", weeklyRankAwards.enabled());
        weekly.put("day", weeklyRankAwards.day().name());
        weekly.put("time", weeklyRankAwards.time().toString());
        weekly.put("zone", weeklyRankAwards.zone().getId());
        root.put("weekly-rank-awards", weekly);

        Map<String, Object> monthly = new LinkedHashMap<>();
        monthly.put("enabled", monthlyRankAwards.enabled());
        monthly.put("day", monthlyRankAwards.dayOfMonth());
        monthly.put("time", monthlyRankAwards.time().toString());
        monthly.put("zone", monthlyRankAwards.zone().getId());
        root.put("monthly-rank-awards", monthly);

        try (Writer writer = Files.newBufferedWriter(path)) {
            new Yaml().dump(root, writer);
        }
    }

    public record WeeklyRankAwards(boolean enabled, DayOfWeek day, LocalTime time, ZoneId zone) {
    }

    public record MonthlyRankAwards(boolean enabled, int dayOfMonth, LocalTime time, ZoneId zone) {
    }
}
