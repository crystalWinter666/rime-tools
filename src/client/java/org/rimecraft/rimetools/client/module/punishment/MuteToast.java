package org.rimecraft.rimetools.client.module.punishment;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.rimecraft.rimetools.client.ui.RimeUi;

/** Short, centered HUD notice shown while a muted player tries to chat. */
public final class MuteToast implements HudElement {
    private static final int HOLD_TICKS = 80;
    private static final int WIDTH = 220;
    private static final int HEIGHT = 20;

    private long remainingSeconds;
    private int ticksLeft;

    public void show(long remaining) {
        this.remainingSeconds = remaining;
        this.ticksLeft = HOLD_TICKS;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (ticksLeft <= 0) return;
        ticksLeft--;
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int x = (screenWidth - WIDTH) / 2;
        int y = 24;
        RimeUi.roundedRect(graphics, x, y, WIDTH, HEIGHT, RimeUi.OVERLAY);
        RimeUi.roundedOutline(graphics, x, y, WIDTH, HEIGHT, RimeUi.DANGER);
        Component text = Component.translatable("rime-tools.punish.muted_notice", remainingSeconds);
        graphics.centeredText(minecraft.font, text, x + WIDTH / 2, y + 6, RimeUi.DANGER);
    }
}
