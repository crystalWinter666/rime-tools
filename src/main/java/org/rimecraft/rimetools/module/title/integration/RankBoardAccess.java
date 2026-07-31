package org.rimecraft.rimetools.module.title.integration;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RankBoardAccess {
    private static final String MOD_CLASS = "cn.bamgdam.rankboard.RankBoardMod";
    private static final String METRIC_CLASS = MOD_CLASS + "$Metric";
    private static final String STATE_CLASS = "cn.bamgdam.rankboard.LeaderboardState";
    private static final String READER_CLASS = "cn.bamgdam.rankboard.StatReader";
    private static final String COLORS_CLASS = "cn.bamgdam.rankboard.RankBoardColors";

    private final Class<?> metricClass;
    private final Method stateGet;
    private final Method range;
    private final Method readerReady;
    private final Method readAll;
    private final Method included;
    private final Method renderedRgb;
    private final Field metricCommand;
    private final Method metricLabel;

    private RankBoardAccess() throws ReflectiveOperationException {
        Class<?> modClass = Class.forName(MOD_CLASS);
        metricClass = Class.forName(METRIC_CLASS);
        Class<?> stateClass = Class.forName(STATE_CLASS);
        Class<?> readerClass = Class.forName(READER_CLASS);
        Class<?> colorsClass = Class.forName(COLORS_CLASS);

        stateGet = stateClass.getMethod("get", MinecraftServer.class);
        range = stateClass.getMethod("range", MinecraftServer.class, LocalDate.class, LocalDate.class,
                metricClass, boolean.class);
        readerReady = accessible(readerClass.getDeclaredMethod("isReady"));
        readAll = accessible(readerClass.getDeclaredMethod("readAll", MinecraftServer.class, metricClass));
        included = accessible(modClass.getDeclaredMethod("isIncluded", MinecraftServer.class, stateClass,
                UUID.class, String.class));
        renderedRgb = accessible(colorsClass.getDeclaredMethod("renderedRgb", metricClass));
        metricCommand = accessible(metricClass.getDeclaredField("command"));
        metricLabel = accessible(metricClass.getDeclaredMethod("label"));
    }

    public static RankBoardAccess create() {
        try {
            return new RankBoardAccess();
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    public List<BoardRanking> rankings(MinecraftServer server, LocalDate from, LocalDate to, int limit)
            throws ReflectiveOperationException {
        if (!Boolean.TRUE.equals(readerReady.invoke(null))) {
            throw new NotReadyException("RankBoard history cache is not ready");
        }
        Object state = stateGet.invoke(null, server);
        Map<UUID, String> names = readNames(server);
        List<BoardRanking> result = new ArrayList<>();
        for (Object metric : metricClass.getEnumConstants()) {
            Object rangeData = range.invoke(state, server, from, to, metric, false);
            Method valuesMethod = accessible(rangeData.getClass().getDeclaredMethod("values"));
            @SuppressWarnings("unchecked")
            Map<UUID, Long> values = (Map<UUID, Long>) valuesMethod.invoke(rangeData);
            List<Winner> winners = values.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .filter(entry -> isIncluded(server, state, entry.getKey(), names.get(entry.getKey())))
                    .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(entry -> names.getOrDefault(entry.getKey(), entry.getKey().toString())))
                    .limit(limit)
                    .map(entry -> new Winner(entry.getKey(), names.getOrDefault(entry.getKey(),
                            entry.getKey().toString()), entry.getValue()))
                    .toList();
            result.add(new BoardRanking(
                    String.valueOf(metricCommand.get(metric)).toLowerCase(Locale.ROOT),
                    String.valueOf(metricLabel.invoke(metric)),
                    String.format(Locale.ROOT, "#%06X", ((Number) renderedRgb.invoke(null, metric)).intValue() & 0xFFFFFF),
                    winners));
        }
        return List.copyOf(result);
    }

    private Map<UUID, String> readNames(MinecraftServer server) throws ReflectiveOperationException {
        Map<UUID, String> names = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object> snapshots = (List<Object>) readAll.invoke(null, server, (Object) null);
        for (Object snapshot : snapshots) {
            Method uuid = accessible(snapshot.getClass().getDeclaredMethod("uuid"));
            Method name = accessible(snapshot.getClass().getDeclaredMethod("name"));
            names.put((UUID) uuid.invoke(snapshot), String.valueOf(name.invoke(snapshot)));
        }
        return names;
    }

    private boolean isIncluded(MinecraftServer server, Object state, UUID uuid, String name) {
        if (name == null || name.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(included.invoke(null, server, state, uuid, name));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }

    public record BoardRanking(String metricId, String label, String color, List<Winner> winners) {
    }

    public record Winner(UUID playerId, String playerName, long value) {
    }

    public static final class NotReadyException extends ReflectiveOperationException {
        public NotReadyException(String message) {
            super(message);
        }
    }
}
