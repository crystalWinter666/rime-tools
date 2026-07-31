package org.rimecraft.rimetools.module.title.integration;

import net.minecraft.server.MinecraftServer;
import org.rimecraft.rimetools.module.title.config.TitleConfig;
import org.rimecraft.rimetools.module.title.permission.PermissionChecker;
import org.rimecraft.rimetools.module.title.storage.TitleRepository;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RankBoardAwardService {
    static final int WEEKLY_TOP_LIMIT = 5;
    static final int MONTHLY_TOP_LIMIT = 10;
    private static final int CHECK_INTERVAL_TICKS = 1200;
    private static final int RETRY_INTERVAL_TICKS = 6000;

    private final TitleConfig.WeeklyRankAwards weeklyConfig;
    private final TitleConfig.MonthlyRankAwards monthlyConfig;
    private final Logger logger;
    private final RankBoardAccess rankBoard;
    private int ticksUntilCheck;
    private boolean running;

    public RankBoardAwardService(TitleConfig config, Logger logger) {
        this.weeklyConfig = config.weeklyRankAwards();
        this.monthlyConfig = config.monthlyRankAwards();
        this.logger = logger;
        rankBoard = (weeklyConfig.enabled() || monthlyConfig.enabled()) ? RankBoardAccess.create() : null;
    }

    public boolean available() {
        return rankBoard != null;
    }

    public void tick(MinecraftServer server, TitleRepository repository, PermissionChecker permissions) {
        if (rankBoard == null || running || repository == null || !permissions.available()) return;
        if (!weeklyConfig.enabled() && !monthlyConfig.enabled()) return;
        if (ticksUntilCheck-- > 0) return;
        ticksUntilCheck = CHECK_INTERVAL_TICKS;

        try {
            if (weeklyConfig.enabled()) {
                ZonedDateTime now = ZonedDateTime.now(weeklyConfig.zone());
                LocalDate settlementDate = weeklySettlementDate(now, weeklyConfig);
                LocalDate completed = parseDate(repository.state().lastWeeklySettlement());
                if (completed == null || settlementDate.isAfter(completed)) {
                    settleWeekly(server, repository, permissions, settlementDate);
                    return;
                }
            }
            if (monthlyConfig.enabled()) {
                ZonedDateTime now = ZonedDateTime.now(monthlyConfig.zone());
                LocalDate settlementDate = monthlySettlementDate(now, monthlyConfig);
                LocalDate completed = parseDate(repository.state().lastMonthlySettlement());
                if (completed == null || settlementDate.isAfter(completed)) {
                    settleMonthly(server, repository, permissions, settlementDate);
                }
            }
        } catch (RankBoardAccess.NotReadyException exception) {
            ticksUntilCheck = RETRY_INTERVAL_TICKS;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ticksUntilCheck = RETRY_INTERVAL_TICKS;
            logger.warn("Could not settle RankBoard titles for the current period", rootCause(exception));
        }
    }

    private void settleWeekly(MinecraftServer server, TitleRepository repository, PermissionChecker permissions,
                              LocalDate settlementDate) throws ReflectiveOperationException {
        LocalDate from = settlementDate.minusDays(7);
        LocalDate to = settlementDate.minusDays(1);
        var rankings = rankBoard.rankings(server, from, to, WEEKLY_TOP_LIMIT);
        Set<String> managedTitles = new HashSet<>();
        Map<UUID, Set<String>> nextGrants = new HashMap<>();

        for (RankBoardAccess.BoardRanking board : rankings) {
            for (int rank = 1; rank <= WEEKLY_TOP_LIMIT; rank++) {
                String titleId = weeklyTitleId(board.metricId(), rank);
                managedTitles.add(titleId);
                repository.put(new TitleDefinition(titleId, weeklyDisplayName(board.label(), rank),
                        weeklyTitleColor(board.color(), rank), 20_000 - rank, true, weeklyGradientRank(rank)));
            }
            for (int index = 0; index < board.winners().size(); index++) {
                String titleId = weeklyTitleId(board.metricId(), index + 1);
                nextGrants.computeIfAbsent(board.winners().get(index).playerId(), ignored -> new HashSet<>())
                        .add(titleId);
            }
        }

        Map<UUID, Set<String>> replacements = new HashMap<>();
        repository.state().weeklyAwards().forEach((player, titles) -> {
            replacements.put(player, new HashSet<>());
            managedTitles.addAll(titles);
        });
        nextGrants.forEach((player, titles) -> replacements.put(player, Set.copyOf(titles)));

        running = true;
        permissions.replaceManagedGrants(Set.copyOf(managedTitles), Map.copyOf(replacements))
                .whenComplete((success, error) -> server.execute(() -> {
                    running = false;
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        ticksUntilCheck = RETRY_INTERVAL_TICKS;
                        logger.error("Failed to rotate weekly RankBoard title permissions for {}", settlementDate, error);
                        return;
                    }
                    repository.state().completeWeeklySettlement(settlementDate.toString(), nextGrants);
                    int grants = nextGrants.values().stream().mapToInt(Set::size).sum();
                    logger.info("Settled weekly RankBoard titles for {} to {}: {} boards, {} grants",
                            from, to, rankings.size(), grants);
                }));
    }

    private void settleMonthly(MinecraftServer server, TitleRepository repository, PermissionChecker permissions,
                               LocalDate settlementDate) throws ReflectiveOperationException {
        LocalDate from = settlementDate.minusMonths(1);
        LocalDate to = settlementDate.minusDays(1);
        var rankings = rankBoard.rankings(server, from, to, MONTHLY_TOP_LIMIT);
        Map<UUID, Set<String>> nextGrants = new HashMap<>();

        for (RankBoardAccess.BoardRanking board : rankings) {
            for (int rank = 1; rank <= MONTHLY_TOP_LIMIT; rank++) {
                String titleId = monthlyTitleId(board.metricId(), rank);
                repository.put(new TitleDefinition(titleId, monthlyDisplayName(from, board.label(), rank),
                        monthlyTitleColor(board.color(), rank), 30_000 - rank, true, monthlyGradientRank(rank)));
            }
            for (int index = 0; index < board.winners().size(); index++) {
                String titleId = monthlyTitleId(board.metricId(), index + 1);
                nextGrants.computeIfAbsent(board.winners().get(index).playerId(), ignored -> new HashSet<>())
                        .add(titleId);
            }
        }

        running = true;
        permissions.grantManaged(Map.copyOf(nextGrants))
                .whenComplete((success, error) -> server.execute(() -> {
                    running = false;
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        ticksUntilCheck = RETRY_INTERVAL_TICKS;
                        logger.error("Failed to grant monthly RankBoard title permissions for {}", settlementDate, error);
                        return;
                    }
                    repository.state().completeMonthlySettlement(settlementDate.toString());
                    int grants = nextGrants.values().stream().mapToInt(Set::size).sum();
                    logger.info("Settled monthly RankBoard titles for {} to {}: {} boards, {} grants",
                            from, to, rankings.size(), grants);
                }));
    }

    static LocalDate weeklySettlementDate(ZonedDateTime now, TitleConfig.WeeklyRankAwards config) {
        int daysSince = Math.floorMod(now.getDayOfWeek().getValue() - config.day().getValue(), 7);
        LocalDate candidate = now.toLocalDate().minusDays(daysSince);
        if (daysSince == 0 && now.toLocalTime().isBefore(config.time())) candidate = candidate.minusWeeks(1);
        return candidate;
    }

    static LocalDate monthlySettlementDate(ZonedDateTime now, TitleConfig.MonthlyRankAwards config) {
        LocalDate current = now.toLocalDate();
        boolean beforeSettlementTime = current.getDayOfMonth() < config.dayOfMonth()
                || (current.getDayOfMonth() == config.dayOfMonth() && now.toLocalTime().isBefore(config.time()));
        return beforeSettlementTime ? current.minusMonths(1).withDayOfMonth(1) : current.withDayOfMonth(1);
    }

    static String weeklyTitleId(String metricId, int rank) {
        return "weekly_" + metricId + "_t" + rank;
    }

    static String monthlyTitleId(String metricId, int rank) {
        return "monthly_" + metricId + "_t" + rank;
    }

    static String weeklyDisplayName(String boardLabel, int rank) {
        return "周" + boardLabel + "T" + rank;
    }

    static String monthlyDisplayName(LocalDate month, String boardLabel, int rank) {
        return String.format(Locale.ROOT, "%d年%d月%sT%d",
                month.getYear() % 100, month.getMonthValue(), boardLabel, rank);
    }

        static boolean weeklyGradientRank(int rank) {
        return rank == 1;
    }

    static String weeklyTitleColor(String boardColor, int rank) {
        return rank == 1 ? boardColor : "#FFD700";
    }

        static boolean monthlyGradientRank(int rank) {
        return rank <= 3;
    }

    static String monthlyTitleColor(String boardColor, int rank) {
        if (rank == 1) return boardColor;
        if (rank <= 3) return "#FFD700";
        return "#55FF55";
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null && result.getCause() != result) result = result.getCause();
        return result;
    }
}
