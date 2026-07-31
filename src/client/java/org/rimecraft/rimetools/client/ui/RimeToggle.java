package org.rimecraft.rimetools.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class RimeToggle extends AbstractButton {

    private final BooleanSupplier value;
    private final Runnable action;

    RimeToggle(int x, int y, int width, int height, Component message,
               BooleanSupplier value, Runnable action) {
        super(x, y, width, height, message);
        this.value = value;
        this.action = action;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean on = value.getAsBoolean();
        int background = isHoveredOrFocused() ? RimeUi.SURFACE_HOVER : RimeUi.SURFACE;
        int border = isHoveredOrFocused() ? RimeUi.BLUE : RimeUi.BORDER_SOFT;
        RimeUi.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), background);
        RimeUi.roundedOutline(graphics, getX(), getY(), getWidth(), getHeight(), border);

        var font = Minecraft.getInstance().font;
        int trackWidth = 26;
        int trackHeight = 14;
        int trackX = getRight() - trackWidth - 8;
        int trackY = getY() + (getHeight() - trackHeight) / 2;
        String label = getMessage().getString();
        int labelWidth = Math.max(0, trackX - getX() - 13);
        if (font.width(label) > labelWidth) {
            String ellipsis = "...";
            label = font.plainSubstrByWidth(label,
                    Math.max(0, labelWidth - font.width(ellipsis))) + ellipsis;
        }
        graphics.text(font, label, getX() + 9,
                getY() + (getHeight() - font.lineHeight) / 2 + 1, RimeUi.TEXT, false);

        RimeUi.roundedRect(graphics, trackX, trackY, trackWidth, trackHeight,
                on ? RimeUi.ACCENT : 0xFF44505E);
        int knobX = on ? trackX + trackWidth - 11 : trackX + 3;
        RimeUi.roundedRect(graphics, knobX, trackY + 3, 8, 8, on ? 0xFF082033 : 0xFFD7DEE7);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
