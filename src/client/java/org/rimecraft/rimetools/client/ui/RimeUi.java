package org.rimecraft.rimetools.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class RimeUi {

    public static final int OVERLAY = 0x99070A0F;
    public static final int PANEL = 0xF2131820;
    public static final int SURFACE = 0xFF1C232D;
    public static final int SURFACE_HOVER = 0xFF252E3A;
    public static final int BORDER = 0xFF3A4656;
    public static final int BORDER_SOFT = 0xFF293340;
    public static final int TEXT = 0xFFF4F7FA;
    public static final int MUTED = 0xFF9AA7B7;
    public static final int FAINT = 0xFF687586;
    public static final int ACCENT = 0xFF38BDF8;
    public static final int ACCENT_HOVER = 0xFF7DD3FC;
    public static final int BLUE = 0xFF60A5FA;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int DANGER = 0xFFFB7185;
    public static final int DANGER_HOVER = 0xFFFDA4AF;

    private RimeUi() {
    }

    public static AbstractButton button(int x, int y, int width, int height, Component message,
                                        Style style, Runnable action) {
        return button(x, y, width, height, message, style, false, action);
    }

    public static AbstractButton button(int x, int y, int width, int height, Component message,
                                        Style style, boolean selected, Runnable action) {
        return new RimeButton(x, y, width, height, message, style, selected, action);
    }

    public static AbstractButton toggle(int x, int y, int width, int height, Component message,
                                        BooleanSupplier value, Runnable action) {
        return new RimeToggle(x, y, width, height, message, value, action);
    }

    public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        roundedRect(graphics, x + 4, y + 5, width, height, 0x55000000);
        roundedRect(graphics, x + 2, y + 3, width, height, 0x33000000);
    }

    public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        if (width < 5 || height < 5) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + 1, y + height - 2, color);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    public static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public enum Style {PRIMARY, SECONDARY, DANGER, GHOST, TAB}
}
