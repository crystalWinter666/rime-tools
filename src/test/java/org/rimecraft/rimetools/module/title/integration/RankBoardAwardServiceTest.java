package org.rimecraft.rimetools.module.title.integration;

import org.junit.jupiter.api.Test;
import org.rimecraft.rimetools.module.title.config.TitleConfig;

import java.time.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RankBoardAwardServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final TitleConfig.WeeklyRankAwards WEEKLY = new TitleConfig.WeeklyRankAwards(
            true, DayOfWeek.MONDAY, LocalTime.of(0, 5), ZONE);
    private static final TitleConfig.MonthlyRankAwards MONTHLY = new TitleConfig.MonthlyRankAwards(
            true, 1, LocalTime.of(0, 5), ZONE);

    @Test
    void usesPreviousMondayBeforeSettlementTime() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 3, 0, 4, 59, 0, ZONE);

        assertEquals(LocalDate.of(2026, 7, 27), RankBoardAwardService.weeklySettlementDate(now, WEEKLY));
    }

    @Test
    void usesCurrentMondayAfterSettlementTime() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 3, 0, 5, 0, 0, ZONE);

        assertEquals(LocalDate.of(2026, 8, 3), RankBoardAwardService.weeklySettlementDate(now, WEEKLY));
    }

    @Test
    void usesFirstDayOfPreviousMonthBeforeSettlementTime() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 1, 0, 4, 59, 0, ZONE);

        assertEquals(LocalDate.of(2026, 7, 1), RankBoardAwardService.monthlySettlementDate(now, MONTHLY));
    }

    @Test
    void usesFirstDayOfCurrentMonthAfterSettlementTime() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 1, 0, 5, 0, 0, ZONE);

        assertEquals(LocalDate.of(2026, 8, 1), RankBoardAwardService.monthlySettlementDate(now, MONTHLY));
    }

    @Test
    void createsStableWeeklyNamesAndRankColors() {
        assertEquals("weekly_food_t1", RankBoardAwardService.weeklyTitleId("food", 1));
        assertEquals("周大胃王榜T1", RankBoardAwardService.weeklyDisplayName("大胃王榜", 1));
        assertEquals("#55FFFF", RankBoardAwardService.weeklyTitleColor("#55FFFF", 1));
        assertEquals("#FFD700", RankBoardAwardService.weeklyTitleColor("#55FFFF", 2));
        assertEquals("#FFD700", RankBoardAwardService.weeklyTitleColor("#55FFFF", 3));
        assertEquals("#FFD700", RankBoardAwardService.weeklyTitleColor("#55FFFF", 5));
        assertEquals(true, RankBoardAwardService.weeklyGradientRank(1));
        assertEquals(false, RankBoardAwardService.weeklyGradientRank(2));
    }

    @Test
    void createsStableMonthlyNamesAndRankColors() {
        assertEquals("monthly_food_t10", RankBoardAwardService.monthlyTitleId("food", 10));
        assertEquals("26年7月大胃王榜T1",
                RankBoardAwardService.monthlyDisplayName(LocalDate.of(2026, 7, 1), "大胃王榜", 1));
        assertEquals("#55FFFF", RankBoardAwardService.monthlyTitleColor("#55FFFF", 1));
        assertEquals("#FFD700", RankBoardAwardService.monthlyTitleColor("#55FFFF", 2));
        assertEquals("#FFD700", RankBoardAwardService.monthlyTitleColor("#55FFFF", 3));
        assertEquals("#55FF55", RankBoardAwardService.monthlyTitleColor("#55FFFF", 4));
        assertEquals("#55FF55", RankBoardAwardService.monthlyTitleColor("#55FFFF", 10));
        assertEquals(true, RankBoardAwardService.monthlyGradientRank(1));
        assertEquals(true, RankBoardAwardService.monthlyGradientRank(2));
        assertEquals(true, RankBoardAwardService.monthlyGradientRank(3));
        assertEquals(false, RankBoardAwardService.monthlyGradientRank(4));
    }

    @Test
    void fallsBackGracefullyWhenRankBoardIsAbsent() {
        assertNull(RankBoardAccess.create());
    }
}
