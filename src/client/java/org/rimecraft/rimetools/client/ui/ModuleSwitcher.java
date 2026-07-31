package org.rimecraft.rimetools.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ModuleSwitcher {
    private static final int ROW_HEIGHT = 22;

    private final String currentId;
    private boolean open;
    private int x;
    private int y;
    private int width;
    private int height;

    public ModuleSwitcher(String currentId) {
        this.currentId = currentId;
    }

    private static Component trim(Font font, Component value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return Component.literal(font.plainSubstrByWidth(value.getString(), Math.max(0, maxWidth - 8)) + "...");
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(54, width);
        this.height = height;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        List<ClientGuiRegistry.Entry> entries = ClientGuiRegistry.entries();
        if (entries.isEmpty()) return;
        ClientGuiRegistry.Entry current = entries.stream()
                .filter(entry -> entry.id().equals(currentId)).findFirst().orElse(entries.getFirst());
        boolean hovered = RimeUi.contains(mouseX, mouseY, x, y, width, height);
        RimeUi.roundedRect(graphics, x, y, width, height, hovered || open ? RimeUi.SURFACE_HOVER : RimeUi.SURFACE);
        RimeUi.roundedOutline(graphics, x, y, width, height, open ? RimeUi.ACCENT : RimeUi.BORDER);
        Component label = trim(font, current.label(), width - 22);
        graphics.text(font, label, x + 7, y + (height - font.lineHeight) / 2 + 1, RimeUi.TEXT, false);
        graphics.text(font, open ? "^" : "v", x + width - 13,
                y + (height - font.lineHeight) / 2 + 1, RimeUi.MUTED, false);
        if (!open) return;

        int popupY = y + height + 3;
        int popupHeight = entries.size() * ROW_HEIGHT + 8;
        RimeUi.shadow(graphics, x, popupY, width, popupHeight);
        RimeUi.roundedRect(graphics, x, popupY, width, popupHeight, RimeUi.PANEL);
        RimeUi.roundedOutline(graphics, x, popupY, width, popupHeight, RimeUi.BORDER);
        for (int index = 0; index < entries.size(); index++) {
            ClientGuiRegistry.Entry entry = entries.get(index);
            int rowY = popupY + 4 + index * ROW_HEIGHT;
            boolean rowHovered = RimeUi.contains(mouseX, mouseY, x + 4, rowY, width - 8, ROW_HEIGHT);
            if (rowHovered || entry.id().equals(currentId)) {
                RimeUi.roundedRect(graphics, x + 4, rowY, width - 8, ROW_HEIGHT,
                        entry.id().equals(currentId) ? 0xFF173247 : RimeUi.SURFACE_HOVER);
            }
            graphics.text(font, trim(font, entry.label(), width - 18), x + 9,
                    rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1,
                    entry.id().equals(currentId) ? RimeUi.ACCENT_HOVER : RimeUi.TEXT, false);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        if (RimeUi.contains(event.x(), event.y(), x, y, width, height)) {
            open = !open;
            return true;
        }
        if (!open) return false;
        int popupY = y + height + 3;
        List<ClientGuiRegistry.Entry> entries = ClientGuiRegistry.entries();
        if (RimeUi.contains(event.x(), event.y(), x, popupY, width, entries.size() * ROW_HEIGHT + 8)) {
            int index = (int) (event.y() - popupY - 4) / ROW_HEIGHT;
            if (index >= 0 && index < entries.size()) {
                ClientGuiRegistry.Entry entry = entries.get(index);
                open = false;
                if (!entry.id().equals(currentId)) ClientGuiRegistry.open(entry.id());
            }
            return true;
        }
        open = false;
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (open && event.isEscape()) {
            open = false;
            return true;
        }
        return false;
    }

    public int left() {
        return x;
    }
}
