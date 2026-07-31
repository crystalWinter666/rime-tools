package org.rimecraft.rimetools.client.module.title;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.rimecraft.rimetools.client.ui.RimeUi;
import org.rimecraft.rimetools.module.title.network.TitlePayloads;
import org.rimecraft.rimetools.module.title.title.TitleInputValidator;

public final class TitleEditorScreen extends Screen {
    private static final String[] COLORS = {
            "#AAAAAA", "#FFFFFF", "#55FFFF", "#55FF55",
            "#FFFF55", "#FFAA00", "#FF5555", "#FF55FF"
    };

    private final TitleScreen parent;
    private final TitlePayloads.TitleEntry existing;
    private int colorIndex;
    private boolean enabled;
    private String draftId;
    private String draftName;
    private String draftWeight;
    private Component error = Component.empty();
    private EditBox idField;
    private EditBox nameField;
    private EditBox weightField;
    private AbstractButton saveButton;

    TitleEditorScreen(TitleScreen parent, TitlePayloads.TitleEntry existing) {
        super(Component.translatable(existing == null
                ? "rime-tools.title.editor.new_title" : "rime-tools.title.editor.edit_title"));
        this.parent = parent;
        this.existing = existing;
        this.colorIndex = existing == null ? 0 : findColor(existing.color());
        this.enabled = existing == null || existing.enabled();
        this.draftId = existing == null ? "" : existing.id();
        this.draftName = existing == null ? "" : existing.displayName();
        this.draftWeight = existing == null ? "0" : Integer.toString(existing.weight());
    }

    private static boolean validWeight(String value) {
        try {
            int weight = Integer.parseInt(value);
            return weight >= -100_000 && weight <= 100_000;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static int findColor(String color) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equalsIgnoreCase(color)) return i;
        }
        return 0;
    }

    private static int parseColor(String color) {
        return Integer.parseInt(color.substring(1), 16);
    }

    @Override
    protected void init() {
        Layout layout = layout();
        idField = field(layout.idX(), layout.identityY(), layout.identityWidth(),
                "rime-tools.title.field.id", 32, draftId);
        idField.setEditable(existing == null);
        idField.setTextColorUneditable(RimeUi.FAINT);
        nameField = field(layout.nameX(), layout.identityY(), layout.identityWidth(),
                "rime-tools.title.field.name", 32, draftName);
        weightField = field(layout.fieldX(), layout.weightY(), layout.fieldWidth(),
                "rime-tools.title.field.weight", 7, draftWeight);

        idField.setResponder(value -> {
            draftId = value;
            updateValidation();
        });
        nameField.setResponder(value -> {
            draftName = value;
            updateValidation();
        });
        weightField.setResponder(value -> {
            draftWeight = value;
            updateValidation();
        });

        addRenderableWidget(RimeUi.toggle(layout.fieldX(), layout.toggleY(), layout.fieldWidth(),
                layout.toggleHeight(), Component.translatable("rime-tools.title.field.enabled"),
                () -> enabled, () -> enabled = !enabled));
        int gap = 8;
        int buttonWidth = (layout.fieldWidth() - gap) / 2;
        saveButton = addRenderableWidget(RimeUi.button(layout.fieldX(), layout.buttonsY(),
                buttonWidth, layout.buttonHeight(), Component.translatable("rime-tools.title.action.save"),
                RimeUi.Style.PRIMARY, this::save));
        addRenderableWidget(RimeUi.button(layout.fieldX() + buttonWidth + gap, layout.buttonsY(),
                layout.fieldWidth() - buttonWidth - gap, layout.buttonHeight(), Component.translatable("gui.cancel"),
                RimeUi.Style.SECONDARY, this::onClose));
        updateValidation();
        setInitialFocus(existing == null ? idField : nameField);
    }

    private EditBox field(int x, int y, int width, String hintKey, int maxLength, String value) {
        EditBox box = new EditBox(font, x + 7, centeredTextY(y, layout().fieldHeight()),
                width - 14, font.lineHeight, Component.translatable(hintKey));
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setTextColor(RimeUi.TEXT);
        box.setHint(Component.translatable(hintKey));
        box.setValue(value);
        return addRenderableWidget(box);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        graphics.fill(0, 0, width, height, RimeUi.OVERLAY);
        RimeUi.shadow(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight());
        RimeUi.roundedRect(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(),
                layout.panelHeight(), RimeUi.PANEL);
        RimeUi.roundedOutline(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(),
                layout.panelHeight(), RimeUi.BORDER_SOFT);
        renderHeader(graphics, layout);
        renderField(graphics, layout.idX(), layout.identityY(), layout.identityWidth(), idField.isFocused());
        renderField(graphics, layout.nameX(), layout.identityY(), layout.identityWidth(), nameField.isFocused());
        renderField(graphics, layout.fieldX(), layout.weightY(), layout.fieldWidth(), weightField.isFocused());
        renderColors(graphics, layout, mouseX, mouseY);
        if (!layout.compact()) renderLabels(graphics, layout);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, Layout layout) {
        int markX = layout.fieldX();
        int markY = layout.panelY() + (layout.compact() ? 7 : 12);
        int accent = existing == null ? RimeUi.ACCENT : RimeUi.BLUE;
        RimeUi.roundedRect(graphics, markX, markY, 22, 22,
                existing == null ? 0xFF173247 : 0xFF1D334B);
        RimeUi.roundedOutline(graphics, markX, markY, 22, 22, accent);
        graphics.centeredText(font, existing == null ? "+" : "...", markX + 11, markY + 7, accent);
        graphics.text(font, title, markX + 30,
                layout.panelY() + (layout.compact() ? 12 : 10), RimeUi.TEXT, false);
        if (!layout.compact() && !error.getString().isEmpty()) {
            graphics.text(font, error, markX + 30, layout.panelY() + 23, RimeUi.DANGER, false);
        }
        graphics.fill(layout.panelX() + 1, layout.headerBottom() - 1,
                layout.panelRight() - 1, layout.headerBottom(), RimeUi.BORDER_SOFT);
    }

    private void renderField(GuiGraphicsExtractor graphics, int x, int y, int fieldWidth, boolean focused) {
        RimeUi.roundedRect(graphics, x, y, fieldWidth, layout().fieldHeight(),
                focused ? 0xFF202B35 : 0xFF181E26);
        RimeUi.roundedOutline(graphics, x, y, fieldWidth, layout().fieldHeight(),
                focused ? RimeUi.ACCENT : RimeUi.BORDER);
    }

    private void renderLabels(GuiGraphicsExtractor graphics, Layout layout) {
        graphics.text(font, Component.translatable("rime-tools.title.field.id"),
                layout.idX(), layout.identityY() - 13, RimeUi.MUTED, false);
        graphics.text(font, Component.translatable("rime-tools.title.field.name"),
                layout.nameX(), layout.identityY() - 13, RimeUi.MUTED, false);
        graphics.text(font, Component.translatable("rime-tools.title.field.weight"),
                layout.fieldX(), layout.weightY() - 13, RimeUi.MUTED, false);
        graphics.text(font, Component.translatable("rime-tools.title.field.color_label"),
                layout.fieldX(), layout.swatchesY() - 13, RimeUi.MUTED, false);
    }

    private void renderColors(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        for (int i = 0; i < COLORS.length; i++) {
            int x = swatchX(layout, i);
            boolean hovered = RimeUi.contains(mouseX, mouseY, x, layout.swatchesY(),
                    layout.swatchWidth(), layout.swatchHeight());
            int value = 0xFF000000 | parseColor(COLORS[i]);
            RimeUi.roundedRect(graphics, x, layout.swatchesY(), layout.swatchWidth(),
                    layout.swatchHeight(), value);
            if (i == colorIndex || hovered) {
                RimeUi.roundedOutline(graphics, x - 2, layout.swatchesY() - 2,
                        layout.swatchWidth() + 4, layout.swatchHeight() + 4,
                        i == colorIndex ? RimeUi.ACCENT_HOVER : RimeUi.TEXT);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean mouseOver) {
        if (super.mouseClicked(event, mouseOver)) return true;
        if (event.button() != 0) return false;
        Layout layout = layout();
        for (int i = 0; i < COLORS.length; i++) {
            if (RimeUi.contains(event.x(), event.y(), swatchX(layout, i), layout.swatchesY(),
                    layout.swatchWidth(), layout.swatchHeight())) {
                colorIndex = i;
                return true;
            }
        }
        if (RimeUi.contains(event.x(), event.y(), layout.idX(), layout.identityY(),
                layout.identityWidth(), layout.fieldHeight()) && existing == null) {
            setFocused(idField);
            return true;
        }
        if (RimeUi.contains(event.x(), event.y(), layout.nameX(), layout.identityY(),
                layout.identityWidth(), layout.fieldHeight())) {
            setFocused(nameField);
            return true;
        }
        if (RimeUi.contains(event.x(), event.y(), layout.fieldX(), layout.weightY(),
                layout.fieldWidth(), layout.fieldHeight())) {
            setFocused(weightField);
            return true;
        }
        return false;
    }

    private void updateValidation() {
        if (saveButton == null || idField == null || nameField == null || weightField == null) return;
        boolean validId = TitleInputValidator.isValidId(idField.getValue());
        boolean validName = TitleInputValidator.isValidDisplayName(nameField.getValue());
        boolean validWeight = validWeight(weightField.getValue());
        saveButton.active = validId && validName && validWeight;
        if (!idField.getValue().isBlank() && !validId || !nameField.getValue().isBlank() && !validName) {
            error = Component.translatable("rime-tools.title.error.invalid_title");
        } else if (!weightField.getValue().isBlank() && !validWeight) {
            error = Component.translatable("rime-tools.title.error.invalid_weight");
        } else {
            error = Component.empty();
        }
    }

    private void save() {
        updateValidation();
        if (!saveButton.active) return;
        TitleClientNetworking.saveTitle(idField.getValue().trim(), nameField.getValue().trim(),
                COLORS[colorIndex], Integer.parseInt(weightField.getValue()), enabled);
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (event.isConfirmation() && saveButton.active) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void resize(int width, int height) {
        if (idField != null) draftId = idField.getValue();
        if (nameField != null) draftName = nameField.getValue();
        if (weightField != null) draftWeight = weightField.getValue();
        super.resize(width, height);
    }

    private int centeredTextY(int y, int height) {
        return y + (height - font.lineHeight) / 2 + 1;
    }

    private int swatchX(Layout layout, int index) {
        return layout.fieldX() + index * (layout.swatchWidth() + layout.swatchGap());
    }

    private Layout layout() {
        int horizontalMargin = Math.clamp(width / 32, 4, 16);
        int verticalMargin = Math.clamp(height / 32, 4, 12);
        int panelWidth = Math.max(1, Math.min(340, width - horizontalMargin * 2));
        int panelHeight = Math.max(1, Math.min(220, height - verticalMargin * 2));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        boolean compact = panelHeight < 250;
        int inset = panelWidth < 300 ? 10 : 18;
        int fieldX = panelX + inset;
        int fieldWidth = Math.max(1, panelWidth - inset * 2);
        int headerHeight = compact ? 36 : 46;
        int fieldHeight = compact ? 22 : 24;
        int identityY = panelY + headerHeight + (compact ? 7 : 18);
        int identityGap = 8;
        int identityWidth = Math.max(1, (fieldWidth - identityGap) / 2);
        int idX = fieldX;
        int nameX = fieldX + identityWidth + identityGap;
        int weightY = identityY + fieldHeight + (compact ? 6 : 18);
        int swatchesY = weightY + fieldHeight + (compact ? 7 : 18);
        int swatchGap = fieldWidth < 240 ? 3 : 6;
        int swatchWidth = Math.max(8, (fieldWidth - swatchGap * (COLORS.length - 1)) / COLORS.length);
        int swatchHeight = compact ? 18 : 20;
        int buttonHeight = compact ? 24 : 26;
        int buttonsY = panelY + panelHeight - buttonHeight - 10;
        int toggleHeight = compact ? 26 : 30;
        int toggleY = Math.min(swatchesY + swatchHeight + (compact ? 6 : 11),
                buttonsY - toggleHeight - 7);
        return new Layout(panelX, panelY, panelWidth, panelHeight, fieldX, fieldWidth,
                compact, panelY + headerHeight, fieldHeight, idX, nameX, identityWidth,
                identityY, weightY, swatchesY, swatchWidth, swatchGap, swatchHeight,
                toggleY, toggleHeight, buttonsY, buttonHeight);
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int fieldX, int fieldWidth, boolean compact, int headerBottom,
                          int fieldHeight, int idX, int nameX, int identityWidth, int identityY,
                          int weightY, int swatchesY, int swatchWidth, int swatchGap,
                          int swatchHeight, int toggleY, int toggleHeight,
                          int buttonsY, int buttonHeight) {
        int panelRight() {
            return panelX + panelWidth;
        }
    }
}
