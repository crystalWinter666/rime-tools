package org.rimecraft.rimetools.module.title.storage;

import org.junit.jupiter.api.Test;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitleStateTest {
    @Test
    void deletingATitleClearsEverySelection() {
        TitleState state = new TitleState();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        state.putTitle(new TitleDefinition("vip", "VIP", "#55FFFF", 10, true));
        state.select(first, "vip");
        state.select(second, "vip");

        state.removeTitle("vip");

        assertFalse(state.titles().containsKey("vip"));
        assertFalse(state.selections().containsKey(first));
        assertFalse(state.selections().containsKey(second));
    }

    @Test
    void disablingATitleCanClearSelectionsWithoutDeletingIt() {
        TitleState state = new TitleState();
        UUID player = UUID.randomUUID();
        state.putTitle(new TitleDefinition("vip", "VIP", "#55FFFF", 10, true));
        state.select(player, "vip");

        state.clearSelectionsFor("vip");

        assertTrue(state.titles().containsKey("vip"));
        assertFalse(state.selections().containsKey(player));
    }

    @Test
    void weeklySettlementReplacesPreviousPlayerGrants() {
        TitleState state = new TitleState();
        UUID previous = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        state.completeWeeklySettlement("2026-07-27", Map.of(previous, Set.of("weekly_food_t1")));

        state.completeWeeklySettlement("2026-08-03", Map.of(current, Set.of("weekly_food_t1")));

        assertEquals("2026-08-03", state.lastWeeklySettlement());
        assertFalse(state.weeklyAwards().containsKey(previous));
        assertEquals(Set.of("weekly_food_t1"), state.weeklyAwards().get(current));
    }

    @Test
    void monthlySettlementRecordsDateWithoutStoringGrants() {
        TitleState state = new TitleState();
        UUID player = UUID.randomUUID();
        state.completeWeeklySettlement("2026-07-27", Map.of(player, Set.of("weekly_food_t1")));

        state.completeMonthlySettlement("2026-08-01");

        assertEquals("2026-08-01", state.lastMonthlySettlement());
        assertEquals("2026-07-27", state.lastWeeklySettlement());
        assertFalse(state.weeklyAwards().isEmpty());
    }
}
