package org.rimecraft.rimetools.module.title.title;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitleSelectionTest {
    @Test
    void acceptsAStableTitleId() {
        UUID player = UUID.randomUUID();
        assertEquals("mvp_plus", new TitleSelection(player, "mvp_plus").titleId());
    }

    @Test
    void rejectsAnInvalidTitleId() {
        assertThrows(IllegalArgumentException.class,
                () -> new TitleSelection(UUID.randomUUID(), "MVP plus"));
    }

    @Test
    void weeklyFirstPlaceTitleUsesMultipleColors() {
        TitleDefinition title = new TitleDefinition(
                "weekly_food_t1", "周大胃王榜T1", "#FFAA00", 10, true, true);
        var component = title.asComponent();

        assertEquals(title.displayName(), component.getString());
        assertNotEquals(component.getSiblings().get(0).getStyle().getColor(),
                component.getSiblings().get(1).getStyle().getColor());
    }

    @Test
    void monthlyFirstPlaceTitleUsesMultipleColors() {
        TitleDefinition title = new TitleDefinition(
                "monthly_food_t1", "26年07月大胃王榜T1", "#FFAA00", 10, true, true);
        var component = title.asComponent();

        assertEquals(title.displayName(), component.getString());
        assertNotEquals(component.getSiblings().get(0).getStyle().getColor(),
                component.getSiblings().get(1).getStyle().getColor());
    }

    @Test
    void nonGradientTitleUsesStoredColor() {
        TitleDefinition title = new TitleDefinition("vip", "VIP", "#55FFFF", 10, true);
        var component = title.asComponent();

        assertEquals(0x55FFFF, component.getStyle().getColor().getValue());
    }
}
