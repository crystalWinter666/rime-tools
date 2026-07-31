package org.rimecraft.rimetools.client.module.title;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.rimecraft.rimetools.client.ui.ModuleSwitcher;
import org.rimecraft.rimetools.client.ui.RimeUi;
import org.rimecraft.rimetools.module.title.TitleModule;
import org.rimecraft.rimetools.module.title.network.TitlePayloads;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TitleScreen extends Screen {
    private static final int SCROLL_BAR_WIDTH = 4;
    private static final int PLAYER_ROW_HEIGHT = 32;
    private final Screen parent;
    private final ModuleSwitcher moduleSwitcher = new ModuleSwitcher(TitleModule.ID);
    private final List<TitlePayloads.TitleEntry> titles = new ArrayList<>();
    private final List<TitlePayloads.PlayerTarget> playerTargets = new ArrayList<>();
    private TitlePayloads.Capabilities capabilities = new TitlePayloads.Capabilities(false, false, false);
    private String fallbackTitle = "";
    private String fallbackColor = "#AAAAAA";
    private Component status = Component.translatable("rime-tools.title.status.loading");
    private int statusColor = RimeUi.MUTED;
    private View view = View.PLAYER;
    private TitlePayloads.TitleEntry selected;
    private String searchQuery = "";
    private String playerTarget = "";
    private String pendingDeleteId;
    private int scrollOffset;
    private int playerDropdownOffset;
    private boolean draggingScrollBar;
    private boolean playerDropdownOpen;
    private boolean requested;
    private boolean suppressSearchResponder;
    private boolean preserveStatusOnNextResponse;
    private boolean assignmentPending;
    private PendingAssignment pendingAssignment;
    private EditBox searchField;
    private AbstractButton dropdownButton;
    private AbstractButton primaryButton;
    private AbstractButton secondaryButton;
    private AbstractButton dangerButton;
    public TitleScreen(Screen parent) {
        super(Component.translatable("rime-tools.title.screen.title"));
        this.parent = parent;
    }

    private static int color(String value) {
        try {
            return Integer.parseInt(value.substring(1), 16);
        } catch (RuntimeException exception) {
            return 0xAAAAAA;
        }
    }

    private static boolean isValidPlayerTarget(String value) {
        String target = value.trim();
        if (target.matches("[A-Za-z0-9_]{1,16}")) return true;
        try {
            java.util.UUID.fromString(target);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        searchField = null;
        dropdownButton = null;
        primaryButton = null;
        secondaryButton = null;
        dangerButton = null;
        TitleClientNetworking.setConsumers(this::acceptResponse, this::acceptResult);
        if (!requested) {
            requested = true;
            if (!TitleClientNetworking.requestTitles()) {
                status = Component.translatable("rime-tools.title.status.unsupported_server");
                statusColor = RimeUi.DANGER;
            }
        }

        Layout layout = layout();
        initSearch(layout);
        initTabs(layout);
        initFooter(layout);
        addRenderableWidget(RimeUi.button(layout.panelRight() - 34, layout.panelY() + 7, 24, 24,
                Component.literal("x"), RimeUi.Style.GHOST, this::onClose));
        int switchWidth = Math.min(104, Math.max(58, layout.contentWidth() / 3));
        moduleSwitcher.setBounds(layout.panelRight() - 40 - switchWidth, layout.panelY() + 7, switchWidth, 24);
        updateActionStates();
        if (searchField != null) setInitialFocus(searchField);
    }

    private void initSearch(Layout layout) {
        if (layout.searchHeight() == 0) return;
        String key = view == View.ASSIGN ? "rime-tools.title.field.player" : "rime-tools.title.field.search";
        String value = view == View.ASSIGN ? playerTarget : searchQuery;
        int dropdownWidth = view == View.ASSIGN ? 30 : 0;
        searchField = new EditBox(font, layout.contentX() + 7,
                centeredTextY(layout.searchY(), layout.searchHeight()),
                layout.contentWidth() - 14 - dropdownWidth, font.lineHeight, Component.translatable(key));
        searchField.setBordered(false);
        searchField.setMaxLength(view == View.ASSIGN ? 36 : 80);
        searchField.setTextColor(RimeUi.TEXT);
        searchField.setHint(Component.translatable(key));
        searchField.setResponder(input -> {
            if (suppressSearchResponder) return;
            if (view == View.ASSIGN) {
                playerTarget = input;
                playerDropdownOpen = true;
                playerDropdownOffset = 0;
            } else {
                searchQuery = input;
                scrollOffset = 0;
            }
            pendingDeleteId = null;
            updateActionStates();
        });
        suppressSearchResponder = true;
        searchField.setValue(value);
        suppressSearchResponder = false;
        addRenderableWidget(searchField);
        if (view == View.ASSIGN) {
            dropdownButton = addRenderableWidget(RimeUi.button(layout.contentRight() - 28,
                    layout.searchY() + 1, 26, layout.searchHeight() - 2, Component.literal("v"),
                    RimeUi.Style.GHOST, () -> playerDropdownOpen = !playerDropdownOpen));
        }
    }

    private void initTabs(Layout layout) {
        List<View> tabs = new ArrayList<>();
        tabs.add(View.PLAYER);
        if (capabilities.canManageTitles()) tabs.add(View.TITLES);
        if (capabilities.canAssignTitles()) tabs.add(View.ASSIGN);
        int gap = 4;
        int tabWidth = Math.max(1, (layout.contentWidth() - gap * (tabs.size() - 1)) / tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            View tab = tabs.get(i);
            addRenderableWidget(RimeUi.button(layout.contentX() + i * (tabWidth + gap), layout.tabsY(),
                    tabWidth, layout.tabHeight(), tabLabel(tab), RimeUi.Style.TAB, tab == view,
                    () -> switchView(tab)));
        }
    }

    private void initFooter(Layout layout) {
        int x = layout.contentX();
        int y = layout.buttonY();
        int buttonHeight = layout.buttonHeight();
        int gap = 6;
        if (view == View.PLAYER) {
            primaryButton = addRenderableWidget(RimeUi.button(x, y,
                    Math.min(148, layout.contentWidth()), buttonHeight,
                    Component.translatable("rime-tools.title.action.select"), RimeUi.Style.PRIMARY,
                    this::selectTitle));
            return;
        }
        if (view == View.TITLES && pendingDeleteId != null) {
            int width = (layout.contentWidth() - gap) / 2;
            dangerButton = addRenderableWidget(RimeUi.button(x, y, width, buttonHeight,
                    Component.translatable("rime-tools.title.action.confirm_delete"), RimeUi.Style.DANGER,
                    this::confirmDelete));
            secondaryButton = addRenderableWidget(RimeUi.button(x + width + gap, y,
                    layout.contentWidth() - width - gap, buttonHeight,
                    Component.translatable("gui.cancel"), RimeUi.Style.SECONDARY,
                    this::cancelDelete));
            return;
        }
        if (view == View.TITLES) {
            int width = (layout.contentWidth() - gap * 2) / 3;
            primaryButton = addRenderableWidget(RimeUi.button(x, y, width, buttonHeight,
                    Component.translatable("rime-tools.title.action.new"), RimeUi.Style.PRIMARY,
                    () -> minecraft.setScreenAndShow(new TitleEditorScreen(this, null))));
            secondaryButton = addRenderableWidget(RimeUi.button(x + width + gap, y, width, buttonHeight,
                    Component.translatable("rime-tools.title.action.edit"), RimeUi.Style.SECONDARY,
                    () -> minecraft.setScreenAndShow(new TitleEditorScreen(this, selected))));
            dangerButton = addRenderableWidget(RimeUi.button(x + (width + gap) * 2, y,
                    layout.contentWidth() - width * 2 - gap * 2, buttonHeight,
                    Component.translatable("rime-tools.title.action.delete"), RimeUi.Style.DANGER,
                    this::requestDelete));
            return;
        }
        int width = (layout.contentWidth() - gap) / 2;
        primaryButton = addRenderableWidget(RimeUi.button(x, y, width, buttonHeight,
                Component.translatable("rime-tools.title.action.grant"), RimeUi.Style.PRIMARY,
                () -> assignTitle(true)));
        dangerButton = addRenderableWidget(RimeUi.button(x + width + gap, y,
                layout.contentWidth() - width - gap, buttonHeight,
                Component.translatable("rime-tools.title.action.revoke"), RimeUi.Style.DANGER,
                () -> assignTitle(false)));
    }

    private void switchView(View target) {
        if (view == target) return;
        view = target;
        pendingDeleteId = null;
        playerDropdownOpen = target == View.ASSIGN;
        playerDropdownOffset = 0;
        scrollOffset = 0;
        selected = null;
        rebuildWidgets();
    }

    private void selectTitle() {
        if (!canSelect()) return;
        TitleClientNetworking.selectTitle(selected.id());
        status = Component.translatable("rime-tools.title.status.saving");
        statusColor = RimeUi.MUTED;
    }

    private void assignTitle(boolean granted) {
        if (selected == null || !isValidPlayerTarget(playerTarget) || assignmentPending) return;
        pendingAssignment = new PendingAssignment(playerTarget.trim(), selected.displayName(), granted);
        assignmentPending = true;
        playerDropdownOpen = false;
        status = Component.translatable("rime-tools.title.status.saving");
        statusColor = RimeUi.MUTED;
        updateActionStates();
        TitleClientNetworking.assignTitle(pendingAssignment.playerTarget(), selected.id(), granted);
    }

    private void requestDelete() {
        if (selected == null) return;
        pendingDeleteId = selected.id();
        status = Component.translatable("rime-tools.title.confirm.delete.message", selected.displayName());
        statusColor = RimeUi.MUTED;
        rebuildWidgets();
    }

    private void confirmDelete() {
        if (pendingDeleteId == null) return;
        TitleClientNetworking.deleteTitle(pendingDeleteId);
        pendingDeleteId = null;
        status = Component.translatable("rime-tools.title.status.saving");
        statusColor = RimeUi.MUTED;
        rebuildWidgets();
    }

    private void cancelDelete() {
        pendingDeleteId = null;
        status = Component.translatable("rime-tools.title.status.ready");
        statusColor = RimeUi.MUTED;
        rebuildWidgets();
    }

    private void updateActionStates() {
        if (view == View.PLAYER && primaryButton != null) {
            primaryButton.active = canSelect();
        } else if (view == View.TITLES && pendingDeleteId == null) {
            if (secondaryButton != null) secondaryButton.active = selected != null;
            if (dangerButton != null) dangerButton.active = selected != null;
        } else if (view == View.ASSIGN) {
            boolean active = !assignmentPending && selected != null && isValidPlayerTarget(playerTarget);
            if (primaryButton != null) primaryButton.active = active;
            if (dangerButton != null) dangerButton.active = active;
        }
    }

    private boolean canSelect() {
        return selected != null && selected.enabled() && selected.unlocked() && !selected.selected();
    }

    private void acceptResponse(TitlePayloads.TitlesResponse response) {
        if (response.protocolVersion() != TitlePayloads.PROTOCOL_VERSION) {
            status = Component.translatable("rime-tools.title.error.protocol");
            statusColor = RimeUi.DANGER;
            return;
        }
        String selectedId = selected == null ? null : selected.id();
        titles.clear();
        titles.addAll(response.titles());
        playerTargets.clear();
        playerTargets.addAll(response.playerTargets());
        selected = titles.stream().filter(title -> title.id().equals(selectedId)).findFirst()
                .orElseGet(() -> titles.stream().filter(TitlePayloads.TitleEntry::selected)
                        .findFirst().orElse(null));
        fallbackTitle = response.fallbackTitle();
        fallbackColor = response.fallbackColor();
        capabilities = response.capabilities();
        if (view == View.TITLES && !capabilities.canManageTitles()
                || view == View.ASSIGN && !capabilities.canAssignTitles()) {
            view = View.PLAYER;
        }
        if (preserveStatusOnNextResponse) {
            preserveStatusOnNextResponse = false;
        } else {
            status = titles.isEmpty()
                    ? Component.translatable("rime-tools.title.status.empty")
                    : capabilities.permissionsAvailable()
                      ? Component.translatable("rime-tools.title.status.ready")
                      : Component.translatable("rime-tools.title.status.permissions_unavailable");
            statusColor = RimeUi.MUTED;
        }
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll(layout()));
        rebuildWidgets();
    }

    private void acceptResult(TitlePayloads.OperationResult result) {
        PendingAssignment assignment = pendingAssignment;
        pendingAssignment = null;
        assignmentPending = false;
        playerDropdownOpen = false;
        if (assignment != null && result.success()) {
            status = Component.translatable(assignment.granted()
                            ? "rime-tools.title.success.assignment_granted"
                            : "rime-tools.title.success.assignment_revoked",
                    assignment.playerTarget(), assignment.titleName());
            preserveStatusOnNextResponse = true;
        } else {
            status = Component.translatable(result.messageKey());
            preserveStatusOnNextResponse = assignment != null;
        }
        statusColor = result.success() ? RimeUi.SUCCESS : RimeUi.DANGER;
        rebuildWidgets();
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
        renderSearch(graphics, layout);
        renderList(graphics, layout, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        renderPlayerDropdown(graphics, layout, mouseX, mouseY);
        moduleSwitcher.render(graphics, font, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, Layout layout) {
        int markX = layout.contentX();
        int markY = layout.panelY() + 8;
        int accent = selected == null ? RimeUi.ACCENT : 0xFF000000 | color(selected.color());
        RimeUi.roundedRect(graphics, markX, markY, 22, 22, 0xFF173247);
        RimeUi.roundedOutline(graphics, markX, markY, 22, 22, accent);
        graphics.centeredText(font, "P", markX + 11, markY + 7, accent);
        int textWidth = Math.max(20, moduleSwitcher.left() - markX - 38);
        graphics.text(font, trim(getTitle(), textWidth), markX + 30,
                layout.panelY() + (layout.compact() ? 13 : 7), RimeUi.TEXT, false);
        if (!layout.compact()) {
            graphics.text(font, trim(headerSubtitle(), textWidth), markX + 30,
                    layout.panelY() + 20, statusColor, false);
        }
        graphics.fill(layout.panelX() + 1, layout.headerBottom() - 1,
                layout.panelRight() - 1, layout.headerBottom(), RimeUi.BORDER_SOFT);
    }

    private Component headerSubtitle() {
        if (view != View.PLAYER) return status;
        TitlePayloads.TitleEntry preview = selected;
        String text = preview == null && fallbackTitle.isBlank()
                ? Component.translatable("rime-tools.title.default_title").getString()
                : preview == null ? fallbackTitle : preview.displayName();
        int titleColor = color(preview == null ? fallbackColor : preview.color());
        return Component.literal("[ ").append(Component.literal(text).withColor(titleColor))
                .append(Component.literal(" ]"));
    }

    private void renderSearch(GuiGraphicsExtractor graphics, Layout layout) {
        if (searchField == null) return;
        RimeUi.roundedRect(graphics, layout.contentX(), layout.searchY(), layout.contentWidth(),
                layout.searchHeight(), searchField.isFocused() ? 0xFF202B35 : 0xFF181E26);
        RimeUi.roundedOutline(graphics, layout.contentX(), layout.searchY(), layout.contentWidth(),
                layout.searchHeight(), searchField.isFocused() ? RimeUi.ACCENT : RimeUi.BORDER);
    }

    private void renderList(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll(layout));
        RimeUi.roundedRect(graphics, layout.contentX(), layout.listTop(), layout.contentWidth(),
                layout.listHeight(), 0xA80D1117);
        RimeUi.roundedOutline(graphics, layout.contentX(), layout.listTop(), layout.contentWidth(),
                layout.listHeight(), RimeUi.BORDER_SOFT);
        List<TitlePayloads.TitleEntry> visible = visibleTitles();
        graphics.enableScissor(layout.contentX() + 1, layout.listTop() + 1,
                layout.contentRight() - 1, layout.listBottom() - 1);
        if (visible.isEmpty()) {
            Component empty = searchQuery.isBlank() || view == View.ASSIGN
                    ? Component.translatable("rime-tools.title.status.empty")
                    : Component.translatable("rime-tools.title.status.no_results");
            graphics.centeredText(font, empty, layout.contentX() + layout.contentWidth() / 2,
                    layout.listTop() + Math.max(4, (layout.listHeight() - font.lineHeight) / 2), RimeUi.MUTED);
        } else {
            int entryWidth = layout.contentWidth() - 13;
            for (int i = 0; i < visible.size(); i++) {
                int y = layout.listTop() + 4 + i * layout.entryHeight() - scrollOffset;
                if (y + layout.entryHeight() <= layout.listTop() || y >= layout.listBottom()) continue;
                renderTitle(graphics, visible.get(i), layout.contentX() + 4, y, entryWidth,
                        layout.entryHeight() - 4, mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        renderScrollBar(graphics, layout, visible.size());
    }

    private void renderTitle(GuiGraphicsExtractor graphics, TitlePayloads.TitleEntry title,
                             int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY) {
        boolean chosen = selected != null && selected.id().equals(title.id());
        boolean hovered = RimeUi.contains(mouseX, mouseY, x, y, entryWidth, entryHeight);
        int background = chosen ? 0xFF173247 : hovered ? RimeUi.SURFACE_HOVER : 0xB0181E26;
        RimeUi.roundedRect(graphics, x, y, entryWidth, entryHeight, background);
        if (chosen || hovered) {
            RimeUi.roundedOutline(graphics, x, y, entryWidth, entryHeight,
                    chosen ? RimeUi.ACCENT : RimeUi.BORDER);
        }
        int titleColor = 0xFF000000 | color(title.color());
        graphics.fill(x + 6, y + 6, x + 9, y + entryHeight - 6, titleColor);
        Component name = Component.literal(title.displayName()).withColor(titleColor);
        Component state = titleState(title);
        int stateWidth = font.width(state);
        int textRight = x + entryWidth - stateWidth - 14;
        graphics.text(font, trim(name, Math.max(10, textRight - x - 16)), x + 15, y + 6,
                RimeUi.TEXT, false);
        graphics.text(font, trim(Component.literal(title.id()), Math.max(10, textRight - x - 16)),
                x + 15, y + entryHeight - font.lineHeight - 5, RimeUi.FAINT, false);
        graphics.text(font, state, x + entryWidth - stateWidth - 8,
                y + (entryHeight - font.lineHeight) / 2 + 1, stateColor(title), false);
    }

    private Component titleState(TitlePayloads.TitleEntry title) {
        if (view != View.PLAYER) {
            return Component.translatable(title.enabled()
                    ? "rime-tools.title.state.enabled" : "rime-tools.title.state.disabled");
        }
        if (title.selected()) return Component.translatable("rime-tools.title.state.selected");
        return Component.translatable(title.unlocked()
                ? "rime-tools.title.state.unlocked" : "rime-tools.title.state.locked");
    }

    private int stateColor(TitlePayloads.TitleEntry title) {
        if (!title.enabled()) return RimeUi.DANGER;
        if (view == View.PLAYER && title.selected()) return RimeUi.ACCENT_HOVER;
        if (view == View.PLAYER && title.unlocked()) return RimeUi.SUCCESS;
        return view == View.PLAYER ? RimeUi.FAINT : RimeUi.MUTED;
    }

    private void renderScrollBar(GuiGraphicsExtractor graphics, Layout layout, int itemCount) {
        int maxScroll = maxScroll(layout);
        if (maxScroll <= 0) return;
        int trackX = layout.contentRight() - SCROLL_BAR_WIDTH - 3;
        int trackY = layout.listTop() + 5;
        int trackHeight = Math.max(1, layout.listHeight() - 10);
        graphics.fill(trackX, trackY, trackX + SCROLL_BAR_WIDTH, trackY + trackHeight, 0xFF202832);
        int contentHeight = itemCount * layout.entryHeight() + 8;
        int thumbHeight = Math.min(trackHeight,
                Math.max(Math.min(18, trackHeight), trackHeight * layout.listHeight() / contentHeight));
        int thumbY = trackY + (int) ((long) scrollOffset * Math.max(0, trackHeight - thumbHeight) / maxScroll);
        RimeUi.roundedRect(graphics, trackX, thumbY, SCROLL_BAR_WIDTH, thumbHeight,
                draggingScrollBar ? RimeUi.ACCENT_HOVER : RimeUi.ACCENT);
    }

    private void renderPlayerDropdown(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        Dropdown dropdown = playerDropdown(layout);
        if (dropdown == null) return;
        List<TitlePayloads.PlayerTarget> players = filteredPlayerTargets();
        playerDropdownOffset = Math.clamp(playerDropdownOffset, 0,
                Math.max(0, players.size() - dropdown.visibleRows()));
        RimeUi.shadow(graphics, dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height());
        RimeUi.roundedRect(graphics, dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height(),
                RimeUi.PANEL);
        RimeUi.roundedOutline(graphics, dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height(),
                RimeUi.BORDER);
        if (players.isEmpty()) {
            graphics.centeredText(font, Component.translatable("rime-tools.title.player.no_results"),
                    dropdown.x() + dropdown.width() / 2,
                    dropdown.y() + (dropdown.height() - font.lineHeight) / 2, RimeUi.MUTED);
            return;
        }
        int end = Math.min(players.size(), playerDropdownOffset + dropdown.visibleRows());
        for (int i = playerDropdownOffset; i < end; i++) {
            TitlePayloads.PlayerTarget player = players.get(i);
            int rowY = dropdown.y() + 4 + (i - playerDropdownOffset) * PLAYER_ROW_HEIGHT;
            boolean hovered = RimeUi.contains(mouseX, mouseY, dropdown.x() + 4, rowY,
                    dropdown.width() - 8, PLAYER_ROW_HEIGHT - 2);
            if (hovered) {
                RimeUi.roundedRect(graphics, dropdown.x() + 4, rowY,
                        dropdown.width() - 8, PLAYER_ROW_HEIGHT - 2, RimeUi.SURFACE_HOVER);
            }
            int stateColor = player.online() ? RimeUi.SUCCESS : RimeUi.FAINT;
            Component state = Component.translatable(player.online()
                    ? "rime-tools.title.player.online" : "rime-tools.title.player.offline");
            int stateWidth = font.width(state);
            graphics.text(font, trim(Component.literal(player.name()),
                            Math.max(20, dropdown.width() - stateWidth - 28)),
                    dropdown.x() + 10, rowY + 5, RimeUi.TEXT, false);
            if (!player.uuid().equals(player.name())) {
                graphics.text(font, trim(Component.literal(player.uuid()), dropdown.width() - 20),
                        dropdown.x() + 10, rowY + 18, RimeUi.FAINT, false);
            }
            graphics.text(font, state, dropdown.x() + dropdown.width() - stateWidth - 10,
                    rowY + 5, stateColor, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean mouseOver) {
        if (moduleSwitcher.mouseClicked(event)) return true;
        Layout layout = layout();
        TitlePayloads.PlayerTarget playerHit = playerTargetAt(layout, event.x(), event.y());
        if (playerHit != null && event.button() == 0) {
            String value = isValidPlayerTarget(playerHit.name()) ? playerHit.name() : playerHit.uuid();
            playerTarget = value;
            suppressSearchResponder = true;
            searchField.setValue(value);
            suppressSearchResponder = false;
            playerDropdownOpen = false;
            updateActionStates();
            return true;
        }
        Dropdown dropdown = playerDropdown(layout);
        if (dropdown != null && event.button() == 0 && RimeUi.contains(event.x(), event.y(),
                dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height())) {
            return true;
        }
        if (view == View.ASSIGN && event.button() == 0 && searchField != null
                && RimeUi.contains(event.x(), event.y(), layout.contentX(), layout.searchY(),
                layout.contentWidth() - 30, layout.searchHeight())) {
            playerDropdownOpen = true;
        }
        if (super.mouseClicked(event, mouseOver)) return true;
        if (playerDropdownOpen && view == View.ASSIGN
                && !RimeUi.contains(event.x(), event.y(), layout.contentX(), layout.searchY(),
                layout.contentWidth(), layout.searchHeight())) {
            playerDropdownOpen = false;
        }
        if (event.button() == 0 && searchField != null
                && RimeUi.contains(event.x(), event.y(), layout.contentX(), layout.searchY(),
                layout.contentWidth(), layout.searchHeight())) {
            setFocused(searchField);
            if (view == View.ASSIGN) playerDropdownOpen = true;
            return true;
        }
        if (event.button() == 0 && isOverScrollBar(layout, event.x(), event.y())) {
            draggingScrollBar = true;
            updateScrollFromMouse(layout, event.y());
            return true;
        }
        TitleHit hit = titleAt(layout, event.x(), event.y());
        if (hit == null || event.button() != 0) return false;
        boolean wasConfirmingDelete = pendingDeleteId != null;
        selected = hit.title();
        pendingDeleteId = null;
        status = Component.translatable("rime-tools.title.status.title_selected", selected.displayName());
        statusColor = RimeUi.MUTED;
        if (wasConfirmingDelete) rebuildWidgets();
        else updateActionStates();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollBar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollBar) {
            updateScrollFromMouse(layout(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        Dropdown dropdown = playerDropdown(layout);
        if (dropdown != null && RimeUi.contains(mouseX, mouseY,
                dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height())) {
            int max = Math.max(0, filteredPlayerTargets().size() - dropdown.visibleRows());
            playerDropdownOffset = Math.clamp(playerDropdownOffset - (int) Math.signum(scrollY), 0, max);
            return true;
        }
        if (!RimeUi.contains(mouseX, mouseY, layout.contentX(), layout.listTop(),
                layout.contentWidth(), layout.listHeight())) return false;
        scrollOffset = Math.clamp(scrollOffset - (int) (scrollY * 28), 0, maxScroll(layout));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (moduleSwitcher.keyPressed(event)) return true;
        if (event.isEscape()) {
            if (playerDropdownOpen) {
                playerDropdownOpen = false;
                return true;
            }
            onClose();
            return true;
        }
        if (event.key() == 264 || event.key() == 265) {
            moveSelection(event.key() == 264 ? 1 : -1);
            return true;
        }
        if (event.isConfirmation()) {
            if (view == View.PLAYER) selectTitle();
            else if (view == View.TITLES && selected != null) {
                minecraft.setScreenAndShow(new TitleEditorScreen(this, selected));
            } else if (view == View.ASSIGN) assignTitle(true);
            return true;
        }
        return super.keyPressed(event);
    }

    private void moveSelection(int direction) {
        List<TitlePayloads.TitleEntry> visible = visibleTitles();
        if (visible.isEmpty()) return;
        int index = selected == null ? -1 : visible.indexOf(selected);
        index = Math.clamp(index + direction, 0, visible.size() - 1);
        selected = visible.get(index);
        Layout layout = layout();
        int top = index * layout.entryHeight();
        int bottom = top + layout.entryHeight() + 8;
        if (top < scrollOffset) scrollOffset = top;
        else if (bottom > scrollOffset + layout.listHeight()) scrollOffset = bottom - layout.listHeight();
        updateActionStates();
    }

    @Override
    public void resize(int width, int height) {
        if (searchField != null) {
            if (view == View.ASSIGN) playerTarget = searchField.getValue();
            else searchQuery = searchField.getValue();
        }
        super.resize(width, height);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll(layout()));
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private void updateScrollFromMouse(Layout layout, double mouseY) {
        int maxScroll = maxScroll(layout);
        if (maxScroll <= 0) return;
        int trackY = layout.listTop() + 5;
        int trackHeight = Math.max(1, layout.listHeight() - 10);
        int contentHeight = visibleTitles().size() * layout.entryHeight() + 8;
        int thumbHeight = Math.min(trackHeight,
                Math.max(Math.min(18, trackHeight), trackHeight * layout.listHeight() / contentHeight));
        double ratio = (mouseY - trackY - thumbHeight / 2.0) / Math.max(1, trackHeight - thumbHeight);
        scrollOffset = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private boolean isOverScrollBar(Layout layout, double mouseX, double mouseY) {
        return maxScroll(layout) > 0 && RimeUi.contains(mouseX, mouseY,
                layout.contentRight() - 10, layout.listTop(), 10, layout.listHeight());
    }

    private TitleHit titleAt(Layout layout, double mouseX, double mouseY) {
        if (!RimeUi.contains(mouseX, mouseY, layout.contentX() + 4, layout.listTop() + 1,
                layout.contentWidth() - 13, layout.listHeight() - 2)) return null;
        int index = (int) (mouseY - layout.listTop() - 4 + scrollOffset) / layout.entryHeight();
        List<TitlePayloads.TitleEntry> visible = visibleTitles();
        if (index < 0 || index >= visible.size()) return null;
        int entryY = layout.listTop() + 4 + index * layout.entryHeight() - scrollOffset;
        if (mouseY >= entryY + layout.entryHeight() - 4) return null;
        return new TitleHit(visible.get(index));
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, visibleTitles().size() * layout.entryHeight() + 8 - layout.listHeight());
    }

    private List<TitlePayloads.TitleEntry> visibleTitles() {
        String query = view == View.ASSIGN ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        return titles.stream()
                .filter(title -> view != View.PLAYER || title.enabled())
                .filter(title -> query.isEmpty()
                        || title.id().toLowerCase(Locale.ROOT).contains(query)
                        || title.displayName().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private List<TitlePayloads.PlayerTarget> filteredPlayerTargets() {
        String query = playerTarget.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return playerTargets;
        return playerTargets.stream()
                .filter(player -> player.name().toLowerCase(Locale.ROOT).contains(query)
                        || player.uuid().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private Dropdown playerDropdown(Layout layout) {
        if (view != View.ASSIGN || !playerDropdownOpen || layout.searchHeight() == 0) return null;
        int y = layout.searchY() + layout.searchHeight() + 3;
        int availableHeight = Math.max(PLAYER_ROW_HEIGHT + 8, layout.listBottom() - y);
        int rows = Math.max(1, Math.min(5, (availableHeight - 8) / PLAYER_ROW_HEIGHT));
        int contentRows = Math.max(1, Math.min(rows, filteredPlayerTargets().size()));
        return new Dropdown(layout.contentX(), y, layout.contentWidth(),
                contentRows * PLAYER_ROW_HEIGHT + 8, contentRows);
    }

    private TitlePayloads.PlayerTarget playerTargetAt(Layout layout, double mouseX, double mouseY) {
        Dropdown dropdown = playerDropdown(layout);
        if (dropdown == null || !RimeUi.contains(mouseX, mouseY,
                dropdown.x() + 4, dropdown.y() + 4, dropdown.width() - 8, dropdown.height() - 8)) return null;
        int row = (int) (mouseY - dropdown.y() - 4) / PLAYER_ROW_HEIGHT;
        List<TitlePayloads.PlayerTarget> players = filteredPlayerTargets();
        int index = playerDropdownOffset + row;
        return index >= 0 && index < players.size() ? players.get(index) : null;
    }

    private Component tabLabel(View tab) {
        return Component.translatable(switch (tab) {
            case PLAYER -> "rime-tools.title.tab.player";
            case TITLES -> "rime-tools.title.tab.titles";
            case ASSIGN -> "rime-tools.title.tab.assign";
        });
    }

    private Component trim(Component component, int maxWidth) {
        String value = component.getString();
        if (font.width(value) <= maxWidth) return component;
        String ellipsis = "...";
        return Component.literal(font.plainSubstrByWidth(value,
                Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis);
    }

    private int centeredTextY(int y, int height) {
        return y + (height - font.lineHeight) / 2 + 1;
    }

    private Layout layout() {
        int horizontalMargin = Math.clamp(width / 32, 4, 12);
        int verticalMargin = Math.clamp(height / 32, 4, 12);
        int panelWidth = Math.max(1, Math.min(440, width - horizontalMargin * 2));
        int panelHeight = Math.max(1, Math.min(320, height - verticalMargin * 2));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        boolean compact = panelHeight < 230;
        int inset = panelWidth < 280 ? 8 : 12;
        int contentX = panelX + inset;
        int contentWidth = Math.max(1, panelWidth - inset * 2);
        int headerHeight = compact ? 36 : 42;
        int headerBottom = panelY + headerHeight;
        int searchHeight = compact ? 22 : 24;
        int searchY = headerBottom + (compact ? 4 : 6);
        int tabHeight = compact ? 20 : 24;
        int tabsY = searchY + searchHeight + 5;
        int footerHeight = compact ? 32 : 39;
        int footerY = panelY + panelHeight - footerHeight;
        int listTop = tabsY + tabHeight + 5;
        int listHeight = Math.max(1, footerY - (compact ? 4 : 6) - listTop);
        int buttonHeight = compact ? 22 : 24;
        int buttonY = footerY + (footerHeight - buttonHeight) / 2;
        int entryHeight = 40;
        return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentWidth,
                compact, headerBottom, searchY, searchHeight, tabsY, tabHeight,
                listTop, listHeight, footerY, buttonY, buttonHeight, entryHeight);
    }

    private enum View {
        PLAYER, TITLES, ASSIGN
    }

    private record TitleHit(TitlePayloads.TitleEntry title) {
    }

    private record Dropdown(int x, int y, int width, int height, int visibleRows) {
    }

    private record PendingAssignment(String playerTarget, String titleName, boolean granted) {
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, boolean compact,
                          int headerBottom, int searchY, int searchHeight, int tabsY, int tabHeight,
                          int listTop, int listHeight, int footerY, int buttonY, int buttonHeight,
                          int entryHeight) {
        int panelRight() {
            return panelX + panelWidth;
        }

        int contentRight() {
            return contentX + contentWidth;
        }

        int listBottom() {
            return listTop + listHeight;
        }
    }
}
