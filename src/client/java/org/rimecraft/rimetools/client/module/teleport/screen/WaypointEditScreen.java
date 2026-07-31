package org.rimecraft.rimetools.client.module.teleport.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.rimecraft.rimetools.client.ui.RimeUi;
import org.rimecraft.rimetools.module.teleport.model.Waypoint;
import org.rimecraft.rimetools.module.teleport.network.WaypointActionPayload;

public final class WaypointEditScreen extends Screen {

    private final WaypointManagerScreen parent;
    private final int scope;
    private final Waypoint existing;
    private EditBox nameField;
    private EditBox aliasField;
    private EditBox descField;
    private AbstractButton saveButton;
    private boolean overwrite;
    private String draftName;
    private String draftAlias;
    private String draftDescription;

    public WaypointEditScreen(WaypointManagerScreen parent, int scope, Waypoint existing) {
        super(Component.translatable(existing == null
                ? "rime-tools.teleport.screen.edit.create_title"
                : "rime-tools.teleport.screen.edit.edit_title"));
        this.parent = parent;
        this.scope = scope;
        this.existing = existing;
        this.draftName = existing == null ? "" : existing.getName();
        this.draftAlias = existing == null || existing.getAlias() == null ? "" : existing.getAlias();
        this.draftDescription = existing == null || existing.getDescription() == null
                ? "" : existing.getDescription();
    }

    @Override
    protected void init() {
        Layout layout = layout();

        nameField = new EditBox(font, layout.nameX() + 7,
                centeredTextY(layout.identityY(), layout.fieldHeight()),
                layout.identityWidth() - 14, font.lineHeight,
                Component.translatable("rime-tools.teleport.screen.edit.name"));
        nameField.setMaxLength(24);
        nameField.setBordered(false);
        nameField.setTextColor(RimeUi.TEXT);
        nameField.setTextColorUneditable(RimeUi.FAINT);
        nameField.setHint(Component.translatable("rime-tools.teleport.screen.edit.name_hint"));
        nameField.setResponder(value -> {
            draftName = value;
            updateSaveState();
        });
        nameField.setValue(draftName);
        if (existing != null) {
            nameField.setEditable(false);
        }

        aliasField = new EditBox(font, layout.aliasX() + 7,
                centeredTextY(layout.identityY(), layout.fieldHeight()),
                layout.identityWidth() - 14, font.lineHeight,
                Component.translatable("rime-tools.teleport.screen.edit.alias"));
        aliasField.setMaxLength(48);
        aliasField.setBordered(false);
        aliasField.setTextColor(RimeUi.TEXT);
        aliasField.setHint(Component.translatable("rime-tools.teleport.screen.edit.alias_hint"));
        aliasField.setResponder(value -> draftAlias = value);
        aliasField.setValue(draftAlias);
        addRenderableWidget(aliasField);
        addRenderableWidget(nameField);

        descField = new EditBox(font, layout.fieldX() + 7,
                centeredTextY(layout.descriptionY(), layout.fieldHeight()),
                layout.fieldWidth() - 14, font.lineHeight,
                Component.translatable("rime-tools.teleport.screen.edit.description"));
        descField.setMaxLength(256);
        descField.setBordered(false);
        descField.setTextColor(RimeUi.TEXT);
        descField.setHint(Component.translatable("rime-tools.teleport.screen.edit.description_hint"));
        descField.setResponder(value -> draftDescription = value);
        descField.setValue(draftDescription);
        addRenderableWidget(descField);

        if (existing == null) {
            addRenderableWidget(RimeUi.toggle(layout.fieldX(), layout.toggleY(), layout.fieldWidth(),
                    layout.toggleHeight(),
                    Component.translatable("rime-tools.teleport.screen.edit.overwrite"), () -> overwrite,
                    () -> overwrite = !overwrite));
        }

        int gap = 8;
        int buttonWidth = (layout.fieldWidth() - gap) / 2;
        saveButton = addRenderableWidget(RimeUi.button(layout.fieldX(), layout.buttonsY(), buttonWidth,
                layout.buttonHeight(),
                Component.translatable("rime-tools.teleport.screen.edit.save"), RimeUi.Style.PRIMARY,
                this::save));
        addRenderableWidget(RimeUi.button(layout.fieldX() + buttonWidth + gap, layout.buttonsY(),
                buttonWidth, layout.buttonHeight(), Component.translatable("rime-tools.teleport.screen.edit.cancel"),
                RimeUi.Style.SECONDARY, this::onClose));
        updateSaveState();
        setInitialFocus(aliasField);
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
        renderField(graphics, layout.nameX(), layout.identityY(), layout.identityWidth(),
                layout.fieldHeight(), nameField.isFocused());
        renderField(graphics, layout.aliasX(), layout.identityY(), layout.identityWidth(),
                layout.fieldHeight(), aliasField.isFocused());
        renderField(graphics, layout.fieldX(), layout.descriptionY(), layout.fieldWidth(),
                layout.fieldHeight(), descField.isFocused());

        if (!layout.compact()) {
            graphics.text(font, Component.translatable("rime-tools.teleport.screen.edit.name"),
                    layout.nameX(), layout.identityY() - 13, RimeUi.MUTED, false);
            graphics.text(font, Component.translatable("rime-tools.teleport.screen.edit.alias"),
                    layout.aliasX(), layout.identityY() - 13, RimeUi.MUTED, false);
            graphics.text(font, Component.translatable("rime-tools.teleport.screen.edit.description"),
                    layout.fieldX(), layout.descriptionY() - 13, RimeUi.MUTED, false);
            String count = descField.getValue().length() + " / 256";
            graphics.text(font, count, layout.fieldX() + layout.fieldWidth() - font.width(count),
                    layout.descriptionY() - 13, RimeUi.FAINT, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, Layout layout) {
        int markX = layout.panelX() + 16;
        int markY = layout.panelY() + (layout.compact() ? 7 : 12);
        RimeUi.roundedRect(graphics, markX, markY, 22, 22,
                existing == null ? 0xFF173247 : 0xFF1D334B);
        RimeUi.roundedOutline(graphics, markX, markY, 22, 22,
                existing == null ? 0xFF38BDF8 : 0xFF365F89);
        graphics.centeredText(font, existing == null ? "+" : "...", markX + 11, markY + 7,
                existing == null ? RimeUi.ACCENT_HOVER : RimeUi.BLUE);
        graphics.text(font, title, markX + 30,
                layout.panelY() + (layout.compact() ? 12 : 10), RimeUi.TEXT, false);
        if (!layout.compact()) {
            graphics.text(font, Component.translatable(scope == 0
                            ? "rime-tools.teleport.screen.edit.personal_scope"
                            : "rime-tools.teleport.screen.edit.global_scope"),
                    markX + 30, layout.panelY() + 23, RimeUi.MUTED, false);
        }
        graphics.fill(layout.panelX() + 1, layout.headerBottom() - 1,
                layout.panelX() + layout.panelWidth() - 1, layout.headerBottom(), RimeUi.BORDER_SOFT);
    }

    private void renderField(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             boolean focused) {
        RimeUi.roundedRect(graphics, x, y, width, height,
                focused ? 0xFF202B35 : 0xFF181E26);
        RimeUi.roundedOutline(graphics, x, y, width, height,
                focused ? RimeUi.ACCENT : RimeUi.BORDER);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean mouseOver) {
        if (super.mouseClicked(event, mouseOver)) return true;
        if (event.button() != 0) return false;

        Layout layout = layout();
        if (existing == null && RimeUi.contains(event.x(), event.y(), layout.nameX(), layout.identityY(),
                layout.identityWidth(), layout.fieldHeight())) {
            setFocused(nameField);
            return true;
        }
        if (RimeUi.contains(event.x(), event.y(), layout.aliasX(), layout.identityY(),
                layout.identityWidth(), layout.fieldHeight())) {
            setFocused(aliasField);
            return true;
        }
        if (RimeUi.contains(event.x(), event.y(), layout.fieldX(), layout.descriptionY(),
                layout.fieldWidth(), layout.fieldHeight())) {
            setFocused(descField);
            return true;
        }
        return false;
    }

    private void updateSaveState() {
        if (saveButton != null && nameField != null) {
            saveButton.active = !nameField.getValue().trim().isEmpty();
        }
    }

    private void save() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;
        String alias = aliasField.getValue().trim();
        if (alias.isEmpty()) alias = null;
        String description = descField.getValue().trim();
        if (description.isEmpty()) description = null;

        if (existing != null) {
            parent.sendAction(WaypointActionPayload.ACTION_EDIT_DESC, scope, name, alias, description, false);
        } else {
            parent.sendAction(WaypointActionPayload.ACTION_CREATE, scope, name, alias, description, overwrite);
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
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
        if (nameField != null) draftName = nameField.getValue();
        if (aliasField != null) draftAlias = aliasField.getValue();
        if (descField != null) draftDescription = descField.getValue();
        super.resize(width, height);
    }

    private int centeredTextY(int fieldY, int fieldHeight) {
        return fieldY + (fieldHeight - font.lineHeight) / 2 + 1;
    }

    private Layout layout() {
        int preferredHeight = existing == null ? 210 : 178;
        int horizontalMargin = Math.clamp(width / 32, 4, 16);
        int verticalMargin = Math.clamp(height / 32, 4, 12);
        int panelWidth = Math.max(1, Math.min(340, width - horizontalMargin * 2));
        int panelHeight = Math.max(1, Math.min(preferredHeight, height - verticalMargin * 2));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        boolean compact = panelHeight < preferredHeight;
        int fieldInset = panelWidth < 280 ? 10 : 18;
        int fieldX = panelX + fieldInset;
        int fieldWidth = Math.max(1, panelWidth - fieldInset * 2);
        int identityGap = 8;
        int identityWidth = (fieldWidth - identityGap) / 2;
        int aliasX = fieldX;
        int nameX = aliasX + identityWidth + identityGap;
        int headerHeight = compact ? 36 : 46;
        int fieldHeight = compact ? 22 : 24;
        int identityY = panelY + headerHeight + (compact ? 7 : 18);
        int descriptionY = identityY + fieldHeight + (compact ? 5 : 17);
        int buttonHeight = compact ? 24 : 26;
        int buttonsY = panelY + panelHeight - buttonHeight - 10;
        int toggleHeight = compact ? 26 : 30;
        int toggleY = Math.min(descriptionY + fieldHeight + (compact ? 5 : 11),
                buttonsY - toggleHeight - 6);
        return new Layout(panelX, panelY, panelWidth, panelHeight, fieldX, fieldWidth,
                nameX, aliasX, identityWidth, compact, panelY + headerHeight, fieldHeight,
                identityY, descriptionY, toggleY, toggleHeight, buttonsY, buttonHeight);
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int fieldX, int fieldWidth, int nameX, int aliasX, int identityWidth,
                          boolean compact, int headerBottom, int fieldHeight,
                          int identityY, int descriptionY, int toggleY, int toggleHeight,
                          int buttonsY, int buttonHeight) {
    }
}
