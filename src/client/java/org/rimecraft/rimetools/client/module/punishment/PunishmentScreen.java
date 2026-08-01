package org.rimecraft.rimetools.client.module.punishment;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.rimecraft.rimetools.client.ui.ModuleSwitcher;
import org.rimecraft.rimetools.client.ui.RimeUi;
import org.rimecraft.rimetools.module.punishment.PunishmentModule;
import org.rimecraft.rimetools.module.punishment.network.PunishmentPayloads;
import org.rimecraft.rimetools.module.punishment.util.DurationParser;

import java.util.ArrayList;
import java.util.List;

/** Compact server-authoritative moderation dashboard. */
public final class PunishmentScreen extends Screen {
    private final Screen parent;
    private final ModuleSwitcher switcher = new ModuleSwitcher(PunishmentModule.ID);
    private final List<PunishmentPayloads.PlayerEntry> players = new ArrayList<>();
    private final List<PunishmentPayloads.RecordEntry> records = new ArrayList<>();
    private EditBox search;
    private EditBox reason;
    private EditBox duration;
    private PunishmentPayloads.PlayerEntry selectedPlayer;
    private PunishmentPayloads.RecordEntry selectedRecord;
    private Component status = Component.translatable("rime-tools.punish.gui.loading");
    private int statusColor = RimeUi.MUTED;
    private int page = 1;
    private int totalPages = 1;
    private int scroll;
    private String searchQuery = "";
    private String reasonText = "";
    private String durationText = "1h";
    private boolean requested;
    private boolean canApply;
    private boolean canRevoke;
    private boolean history;

    public PunishmentScreen(Screen parent) {
        super(Component.translatable("rime-tools.punish.gui.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        clearWidgets();
        PunishmentClientNetworking.setConsumers(this::accept, this::result);
        Layout layout = layout();
        search = new EditBox(font, layout.x + 10, layout.y + 44, layout.width - 20, 18,
                Component.translatable("rime-tools.punish.gui.search"));
        search.setMaxLength(64);
        search.setHint(Component.translatable("rime-tools.punish.gui.search"));
        search.setValue(searchQuery);
        search.setResponder(value -> searchQuery = value);
        addRenderableWidget(search);
        duration = new EditBox(font, layout.x + 10, layout.y + layout.height - 56,
                72, 18, Component.translatable("rime-tools.punish.gui.duration"));
        duration.setMaxLength(12);
        duration.setValue(durationText);
        duration.setResponder(value -> durationText = value);
        duration.setHint(Component.translatable("rime-tools.punish.gui.duration"));
        addRenderableWidget(duration);
        reason = new EditBox(font, layout.x + 88, layout.y + layout.height - 56,
                layout.width - 98, 18, Component.translatable("rime-tools.punish.gui.reason"));
        reason.setMaxLength(256);
        reason.setValue(reasonText);
        reason.setResponder(value -> reasonText = value);
        reason.setHint(Component.translatable("rime-tools.punish.gui.reason"));
        addRenderableWidget(reason);
        addRenderableWidget(RimeUi.button(layout.x + 10, layout.y + 67, 90, 22,
                Component.translatable("rime-tools.punish.gui.players"), RimeUi.Style.TAB, !history, () -> setHistory(false)));
        addRenderableWidget(RimeUi.button(layout.x + 104, layout.y + 67, 90, 22,
                Component.translatable("rime-tools.punish.gui.history"), RimeUi.Style.TAB, history, () -> setHistory(true)));
        int actionY = layout.y + layout.height - 32;
        if (!history) {
            int buttonWidth = Math.max(42, (layout.width - 45) / 6);
            addAction(layout.x + 10, actionY, buttonWidth, "Warn", "WARN", false);
            addAction(layout.x + 15 + buttonWidth, actionY, buttonWidth, "Mute", "MUTE", true);
            addAction(layout.x + 20 + buttonWidth * 2, actionY, buttonWidth, "Temp ban", "TEMP_BAN", true);
            addAction(layout.x + 25 + buttonWidth * 3, actionY, buttonWidth, "Perm ban", "PERMA_BAN", false);
            addAction(layout.x + 30 + buttonWidth * 4, actionY, buttonWidth, "Kick", "KICK", false);
            addAction(layout.x + 35 + buttonWidth * 5, actionY,
                    layout.width - 45 - buttonWidth * 5, "Perm mute", "PERMA_MUTE", false);
        } else {
            AbstractButton revoke = addRenderableWidget(RimeUi.button(layout.x + 10, actionY, 100, 22,
                    Component.translatable("rime-tools.punish.gui.revoke"), RimeUi.Style.DANGER, this::revoke));
            revoke.active = canRevoke && selectedRecord != null && "ACTIVE".equals(selectedRecord.status());
            addRenderableWidget(RimeUi.button(layout.x + 116, actionY, 54, 22, Component.literal("<"),
                    RimeUi.Style.SECONDARY, () -> request(Math.max(1, page - 1))));
            addRenderableWidget(RimeUi.button(layout.x + 174, actionY, 54, 22, Component.literal(">"),
                    RimeUi.Style.SECONDARY, () -> request(Math.min(totalPages, page + 1))));
        }
        addRenderableWidget(RimeUi.button(layout.x + layout.width - 34, layout.y + 8, 24, 22,
                Component.literal("x"), RimeUi.Style.GHOST, this::onClose));
        switcher.setBounds(layout.x + layout.width - 145, layout.y + 8, 105, 22);
        if (!requested) request(page);
    }

    private void addAction(int x, int y, int width, String label, String action, boolean timed) {
        AbstractButton button = addRenderableWidget(RimeUi.button(x, y, width, 22, Component.literal(label),
                action.equals("KICK") || action.equals("PERMA_BAN") ? RimeUi.Style.DANGER : RimeUi.Style.PRIMARY,
                () -> apply(action, timed)));
        button.active = canApply && selectedPlayer != null;
    }

    private void setHistory(boolean value) {
        history = value;
        scroll = 0;
        rebuildWidgets();
    }

    private void request(int requestedPage) {
        page = requestedPage;
        requested = true;
        String query = search == null ? searchQuery : search.getValue();
        searchQuery = query;
        if (!PunishmentClientNetworking.request(query, page)) {
            status = Component.translatable("rime-tools.punish.gui.unsupported");
            statusColor = RimeUi.DANGER;
        }
    }

    private void apply(String action, boolean timed) {
        if (selectedPlayer == null || !canApply) return;
        long seconds = timed ? DurationParser.parseSeconds(duration.getValue()) : 0;
        if (timed && seconds <= 0) {
            status = Component.translatable("rime-tools.punish.invalid_duration", duration.getValue());
            statusColor = RimeUi.DANGER;
            return;
        }
        PunishmentClientNetworking.action(selectedPlayer.uuid(), selectedPlayer.name(), action,
                seconds, reason.getValue(), "");
        status = Component.translatable("rime-tools.punish.gui.working");
        statusColor = RimeUi.MUTED;
    }

    private void revoke() {
        if (selectedRecord == null || !canRevoke) return;
        PunishmentClientNetworking.action(selectedRecord.playerUuid(), selectedRecord.playerName(),
                "REVOKE", 0, reason.getValue(), selectedRecord.id());
    }

    private void accept(PunishmentPayloads.Response response) {
        if (response.version() != PunishmentPayloads.PROTOCOL_VERSION) return;
        players.clear(); players.addAll(response.players());
        records.clear(); records.addAll(response.records());
        page = response.page(); totalPages = response.totalPages();
        canApply = response.canApply(); canRevoke = response.canRevoke();
        status = Component.literal(history ? "Page " + page + " / " + totalPages : players.size() + " players");
        statusColor = RimeUi.MUTED;
        rebuildWidgets();
    }

    private void result(PunishmentPayloads.Result result) {
        status = Component.literal(result.message());
        statusColor = result.success() ? RimeUi.SUCCESS : RimeUi.DANGER;
        if (result.success()) request(1);
        else rebuildWidgets();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Layout l = layout();
        graphics.fill(0, 0, width, height, RimeUi.OVERLAY);
        RimeUi.shadow(graphics, l.x, l.y, l.width, l.height);
        RimeUi.roundedRect(graphics, l.x, l.y, l.width, l.height, RimeUi.PANEL);
        RimeUi.roundedOutline(graphics, l.x, l.y, l.width, l.height, RimeUi.BORDER);
        graphics.text(font, getTitle(), l.x + 12, l.y + 14, RimeUi.TEXT, false);
        graphics.text(font, status, l.x + 12, l.y + 29, statusColor, false);
        renderRows(graphics, l, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        switcher.render(graphics, font, mouseX, mouseY);
    }

    private void renderRows(GuiGraphicsExtractor graphics, Layout l, int mouseX, int mouseY) {
        int top = l.y + 94;
        int bottom = l.y + l.height - 60;
        int rowHeight = 34;
        graphics.enableScissor(l.x + 9, top, l.x + l.width - 9, bottom);
        int count = history ? records.size() : players.size();
        for (int index = 0; index < count; index++) {
            int y = top + index * rowHeight - scroll;
            if (y + 30 <= top || y >= bottom) continue;
            boolean selected = history ? records.get(index) == selectedRecord : players.get(index) == selectedPlayer;
            boolean hovered = RimeUi.contains(mouseX, mouseY, l.x + 10, y, l.width - 20, 30);
            RimeUi.roundedRect(graphics, l.x + 10, y, l.width - 20, 30,
                    selected ? 0xFF173247 : hovered ? RimeUi.SURFACE_HOVER : RimeUi.SURFACE);
            if (history) renderRecord(graphics, records.get(index), l.x + 16, y);
            else renderPlayer(graphics, players.get(index), l.x + 16, y);
        }
        graphics.disableScissor();
    }

    private void renderPlayer(GuiGraphicsExtractor graphics, PunishmentPayloads.PlayerEntry value, int x, int y) {
        graphics.text(font, Component.literal(value.name()), x, y + 5, RimeUi.TEXT, false);
        String state = (value.online() ? "online " : "") + (value.banned() ? "BANNED " : "")
                + (value.muted() ? "MUTED " : "") + "warnings=" + value.warnings();
        graphics.text(font, Component.literal(state), x, y + 17,
                value.banned() || value.muted() ? RimeUi.DANGER : RimeUi.MUTED, false);
    }

    private void renderRecord(GuiGraphicsExtractor graphics, PunishmentPayloads.RecordEntry value, int x, int y) {
        graphics.text(font, Component.literal(value.playerName() + "  " + value.type() + "  " + value.status()),
                x, y + 5, "ACTIVE".equals(value.status()) ? RimeUi.DANGER : RimeUi.TEXT, false);
        graphics.text(font, Component.literal(value.reason().isBlank() ? value.id() : value.reason()),
                x, y + 17, RimeUi.MUTED, false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean mouseOver) {
        if (switcher.mouseClicked(event)) return true;
        if (super.mouseClicked(event, mouseOver)) return true;
        Layout l = layout();
        int top = l.y + 94;
        int bottom = l.y + l.height - 60;
        if (event.button() == 0 && event.y() >= top && event.y() < bottom) {
            int index = (int) (event.y() - top + scroll) / 34;
            if (history && index >= 0 && index < records.size()) selectedRecord = records.get(index);
            if (!history && index >= 0 && index < players.size()) selectedPlayer = players.get(index);
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int count = history ? records.size() : players.size();
        Layout l = layout();
        int viewport = Math.max(1, l.height - 154);
        scroll = Math.clamp(scroll - (int) (scrollY * 28), 0, Math.max(0, count * 34 - viewport));
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (switcher.keyPressed(event)) return true;
        if (event.isEscape()) { onClose(); return true; }
        if (event.isConfirmation()) { request(1); return true; }
        return super.keyPressed(event);
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }

    private Layout layout() {
        int w = Math.min(460, Math.max(330, width - 20));
        int h = Math.min(330, Math.max(220, height - 20));
        return new Layout((width - w) / 2, (height - h) / 2, w, h);
    }
    private record Layout(int x, int y, int width, int height) { }
}
