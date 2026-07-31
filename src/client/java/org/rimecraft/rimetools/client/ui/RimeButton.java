package org.rimecraft.rimetools.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class RimeButton extends AbstractButton {

    private final Runnable action;
    private final RimeUi.Style style;
    private final boolean selected;

    RimeButton(int x, int y, int width, int height, Component message, RimeUi.Style style,
               boolean selected, Runnable action) {
        super(x, y, width, height, message);
        this.action = action;
        this.style = style;
        this.selected = selected;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean highlighted = isHoveredOrFocused();
        int background;
        int border;
        int text;

        switch (style) {
            case PRIMARY -> {
                background = highlighted ? RimeUi.ACCENT_HOVER : RimeUi.ACCENT;
                border = background;
                text = 0xFF071512;
            }
            case DANGER -> {
                background = highlighted ? 0xFF4B2832 : 0xFF34232B;
                border = highlighted ? RimeUi.DANGER_HOVER : RimeUi.DANGER;
                text = highlighted ? RimeUi.DANGER_HOVER : RimeUi.DANGER;
            }
            case GHOST -> {
                background = highlighted ? RimeUi.SURFACE_HOVER : 0x00000000;
                border = highlighted ? RimeUi.BORDER : 0x00000000;
                text = highlighted ? RimeUi.TEXT : RimeUi.MUTED;
            }
            case TAB -> {
                background = selected ? 0xFF173247 : highlighted ? RimeUi.SURFACE_HOVER : 0x00000000;
                border = selected ? RimeUi.ACCENT : highlighted ? RimeUi.BORDER_SOFT : 0x00000000;
                text = selected ? RimeUi.ACCENT_HOVER : highlighted ? RimeUi.TEXT : RimeUi.MUTED;
            }
            default -> {
                background = highlighted ? RimeUi.SURFACE_HOVER : RimeUi.SURFACE;
                border = highlighted ? RimeUi.BLUE : RimeUi.BORDER;
                text = highlighted ? RimeUi.TEXT : 0xFFD9E1EA;
            }
        }

        if (!active) {
            background = RimeUi.SURFACE;
            border = RimeUi.BORDER_SOFT;
            text = RimeUi.FAINT;
        }

        if ((background >>> 24) != 0) RimeUi.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), background);
        if ((border >>> 24) != 0) RimeUi.roundedOutline(graphics, getX(), getY(), getWidth(), getHeight(), border);

        var font = Minecraft.getInstance().font;
        String message = getMessage().getString();
        int availableWidth = Math.max(0, getWidth() - 8);
        if (font.width(message) > availableWidth) {
            String ellipsis = "...";
            message = font.plainSubstrByWidth(message,
                    Math.max(0, availableWidth - font.width(ellipsis))) + ellipsis;
        }
        int textX = getX() + (getWidth() - font.width(message)) / 2;
        int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        graphics.text(font, message, textX, textY, text, false);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
