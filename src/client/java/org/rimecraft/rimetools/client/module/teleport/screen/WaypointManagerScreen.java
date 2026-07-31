package org.rimecraft.rimetools.client.module.teleport.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.rimecraft.rimetools.client.ui.ModuleSwitcher;
import org.rimecraft.rimetools.client.ui.RimeUi;
import org.rimecraft.rimetools.module.teleport.TeleportModule;
import org.rimecraft.rimetools.module.teleport.model.FakePlayerInfo;
import org.rimecraft.rimetools.module.teleport.model.Waypoint;
import org.rimecraft.rimetools.module.teleport.network.OpenWaypointScreenPayload;
import org.rimecraft.rimetools.module.teleport.network.TeleportPlayerTarget;
import org.rimecraft.rimetools.module.teleport.network.TpaAllowlistActionPayload;
import org.rimecraft.rimetools.module.teleport.network.WaypointActionPayload;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WaypointManagerScreen extends Screen {

    private static final int TAB_PERSONAL = 0;
    private static final int TAB_GLOBAL = 1;
    private static final int TAB_PLAYERS = 2;
    private static final int TAB_FAKE_PLAYERS = 3;
    private static final int PLAYER_ROW_HEIGHT = 30;
    private static final int ENTRY_HEIGHT = 40;
    private static final int SCROLL_BAR_WIDTH = 4;
    private static final int ACTION_SIZE = 22;
    private static final int ACTION_GAP = 4;
    private final ModuleSwitcher moduleSwitcher = new ModuleSwitcher(TeleportModule.ID);
    private OpenWaypointScreenPayload data;
    private List<Waypoint> personalWaypoints;
    private List<Waypoint> globalWaypoints;
    private List<FakePlayerInfo> fakePlayers;
    private List<TeleportPlayerTarget> playerTargets;
    private int currentTab;
    private int scrollOffset;
    private boolean draggingScrollBar;
    private String pendingDeleteName;
    private String searchQuery = "";
    private String selectedPlayerId;
    private boolean playerDropdownOpen;
    private int playerDropdownOffset;
    private EditBox searchField;
    private AbstractButton dropdownButton;
    private AbstractButton tpaButton;
    private AbstractButton tpahereButton;
    private AbstractButton lastButton;
    private AbstractButton privateButton;
    private AbstractButton allowlistButton;
    private int filteredCacheTab = -1;
    private String filteredCacheQuery;
    private List<Waypoint> cachedWaypoints = List.of();
    private List<FakePlayerInfo> cachedFakePlayers = List.of();
    private List<TeleportPlayerTarget> cachedPlayerTargets = List.of();

    public WaypointManagerScreen(OpenWaypointScreenPayload data) {
        super(Component.translatable("rime-tools.teleport.screen.waypoints.title"));
        setData(data);
    }

    private static List<Waypoint> sortedCopy(List<Waypoint> waypoints) {
        List<Waypoint> result = new ArrayList<>(waypoints);
        result.sort(Comparator.comparing(Waypoint::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static void renderPlayerAvatar(GuiGraphicsExtractor graphics, String playerName,
                                           int x, int y, int accent) {
        Minecraft minecraft = Minecraft.getInstance();
        RimeUi.roundedRect(graphics, x, y, 24, 24, 0xFF202832);
        PlayerFaceExtractor.extractRenderState(graphics, playerSkin(minecraft, playerName),
                x + 2, y + 2, 20, 0xFFFFFFFF);
        RimeUi.roundedOutline(graphics, x, y, 24, 24, accent);
    }

    private static PlayerSkin playerSkin(Minecraft minecraft, String playerName) {
        var connection = minecraft.getConnection();
        if (connection != null) {
            var info = connection.getPlayerInfoIgnoreCase(playerName);
            if (info != null) return info.getSkin();
        }
        UUID offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
        return DefaultPlayerSkin.get(offlineUuid);
    }

    private static boolean matches(String query, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private static String firstCodePoint(String text) {
        if (text == null || text.isBlank()) return "?";
        int firstEnd = text.offsetByCodePoints(0, 1);
        return text.substring(0, firstEnd);
    }

    private static String coordinateValues(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.0f  %.0f  %.0f", x, y, z);
    }

    private static String displayWorld(String world) {
        int separator = world.indexOf(':');
        return separator >= 0 ? world.substring(separator + 1) : world;
    }

    private void setData(OpenWaypointScreenPayload data) {
        this.data = data;
        this.personalWaypoints = sortedCopy(data.personalWaypoints());
        this.globalWaypoints = sortedCopy(data.globalWaypoints());
        this.fakePlayers = new ArrayList<>(data.fakePlayers());
        this.fakePlayers.sort(Comparator.comparing(FakePlayerInfo::name, String.CASE_INSENSITIVE_ORDER));
        this.playerTargets = new ArrayList<>(data.playerTargets());
        invalidateFilterCache();
    }

    @Override
    protected void init() {
        dropdownButton = null;
        tpaButton = null;
        tpahereButton = null;
        lastButton = null;
        privateButton = null;
        allowlistButton = null;
        Layout layout = layout();

        int dropdownWidth = currentTab == TAB_PLAYERS ? 30 : 0;
        searchField = new EditBox(font, layout.contentX() + 7,
                centeredTextY(layout.searchY(), layout.searchHeight()),
                layout.contentWidth() - 14 - dropdownWidth, font.lineHeight,
                Component.translatable("rime-tools.teleport.screen.waypoints.search"));
        searchField.setBordered(false);
        searchField.setMaxLength(80);
        searchField.setTextColor(RimeUi.TEXT);
        searchField.setHint(Component.translatable(currentTab == TAB_PLAYERS
                ? "rime-tools.teleport.screen.players.search_hint"
                : "rime-tools.teleport.screen.waypoints.search_hint"));
        searchField.setValue(searchQuery);
        searchField.setResponder(value -> {
            if (currentTab == TAB_PLAYERS) {
                TeleportPlayerTarget selected = selectedPlayer();
                if (selected != null && !value.equals(selected.name())
                        && !value.equalsIgnoreCase(selected.uuid().toString())) {
                    selectedPlayerId = null;
                }
            }
            searchQuery = value;
            invalidateFilterCache();
            scrollOffset = 0;
            playerDropdownOffset = 0;
            if (currentTab == TAB_PLAYERS) playerDropdownOpen = true;
            pendingDeleteName = null;
            updatePlayerActions();
        });
        addRenderableWidget(searchField);
        if (currentTab == TAB_PLAYERS) {
            dropdownButton = addRenderableWidget(RimeUi.button(layout.contentRight() - 28,
                    layout.searchY() + 1, 26, layout.searchHeight() - 2, Component.literal("v"),
                    RimeUi.Style.GHOST, () -> playerDropdownOpen = !playerDropdownOpen));
        }

        int tabWidth = Math.max(1, (layout.contentWidth() - 12) / 4);
        addRenderableWidget(RimeUi.button(layout.contentX(), layout.tabsY(), tabWidth, layout.tabHeight(),
                Component.translatable("rime-tools.teleport.screen.waypoints.personal"), RimeUi.Style.TAB,
                currentTab == TAB_PERSONAL, () -> switchTab(TAB_PERSONAL)));
        addRenderableWidget(RimeUi.button(layout.contentX() + tabWidth + 4, layout.tabsY(), tabWidth, layout.tabHeight(),
                Component.translatable("rime-tools.teleport.screen.waypoints.global"), RimeUi.Style.TAB,
                currentTab == TAB_GLOBAL, () -> switchTab(TAB_GLOBAL)));
        addRenderableWidget(RimeUi.button(layout.contentX() + (tabWidth + 4) * 2, layout.tabsY(), tabWidth, layout.tabHeight(),
                Component.translatable("rime-tools.teleport.screen.waypoints.players"), RimeUi.Style.TAB,
                currentTab == TAB_PLAYERS, () -> switchTab(TAB_PLAYERS)));
        addRenderableWidget(RimeUi.button(layout.contentX() + (tabWidth + 4) * 3, layout.tabsY(),
                layout.contentWidth() - (tabWidth + 4) * 3, layout.tabHeight(),
                Component.translatable("rime-tools.teleport.screen.waypoints.fake_players"), RimeUi.Style.TAB,
                currentTab == TAB_FAKE_PLAYERS, () -> switchTab(TAB_FAKE_PLAYERS)));

        if (currentTab == TAB_PLAYERS) {
            initPlayerActions(layout);
        } else if (currentTab != TAB_FAKE_PLAYERS
                && data.mode() != WaypointActionPayload.MODE_OTHER_READ_ONLY) {
            addRenderableWidget(RimeUi.button(layout.contentX(), layout.footerY() + layout.footerButtonOffset(),
                    Math.min(108, layout.contentWidth()), layout.footerButtonHeight(),
                    Component.translatable("rime-tools.teleport.screen.waypoints.new"), RimeUi.Style.PRIMARY,
                    this::createWaypoint));
        }
        addRenderableWidget(RimeUi.button(layout.panelRight() - 34, layout.panelY() + 7, 24, 24,
                Component.literal("x"), RimeUi.Style.GHOST, this::onClose));
        int switchWidth = Math.min(104, Math.max(58, layout.contentWidth() / 3));
        moduleSwitcher.setBounds(layout.panelRight() - 40 - switchWidth, layout.panelY() + 7, switchWidth, 24);
        updatePlayerActions();
    }

    private void initPlayerActions(Layout layout) {
        int gap = 5;
        int width = Math.max(1, (layout.contentWidth() - gap * 3) / 4);
        int y = layout.footerY() + layout.footerButtonOffset();
        int height = layout.footerButtonHeight();
        tpaButton = addRenderableWidget(RimeUi.button(layout.contentX(), y, width, height,
                Component.translatable("rime-tools.teleport.screen.players.tpa"), RimeUi.Style.PRIMARY,
                () -> runPlayerCommand("tpa", true)));
        tpahereButton = addRenderableWidget(RimeUi.button(layout.contentX() + width + gap, y, width, height,
                Component.translatable("rime-tools.teleport.screen.players.tpahere"), RimeUi.Style.SECONDARY,
                () -> runPlayerCommand("tpahere", true)));
        lastButton = addRenderableWidget(RimeUi.button(layout.contentX() + (width + gap) * 2, y, width, height,
                Component.translatable("rime-tools.teleport.screen.players.last"), RimeUi.Style.SECONDARY,
                () -> runPlayerCommand("last", false)));
        privateButton = addRenderableWidget(RimeUi.button(layout.contentX() + (width + gap) * 3, y,
                layout.contentRight() - (layout.contentX() + (width + gap) * 3), height,
                Component.translatable("rime-tools.teleport.screen.players.private"), RimeUi.Style.SECONDARY,
                () -> runPlayerCommand("tpother", false)));
        if (data.canManageTpaAllowlist()) {
            int allowWidth = Math.min(230, Math.max(120, layout.contentWidth() - 32));
            int allowY = Math.max(layout.listTop() + 4, layout.listBottom() - height - 8);
            allowlistButton = addRenderableWidget(RimeUi.button(
                    layout.contentX() + (layout.contentWidth() - allowWidth) / 2,
                    allowY, allowWidth, height,
                    Component.translatable("rime-tools.teleport.screen.players.allow_direct"),
                    RimeUi.Style.SECONDARY, this::toggleAllowlist));
        }
    }

    private void switchTab(int tab) {
        if (currentTab == tab) return;
        currentTab = tab;
        scrollOffset = 0;
        pendingDeleteName = null;
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        graphics.fill(0, 0, width, height, RimeUi.OVERLAY);
        RimeUi.shadow(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight());
        RimeUi.roundedRect(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), RimeUi.PANEL);
        RimeUi.roundedOutline(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), RimeUi.BORDER_SOFT);

        renderHeader(graphics, layout);
        renderSearch(graphics, layout);
        if (currentTab == TAB_PLAYERS) renderPlayerPanel(graphics, layout, mouseX, mouseY);
        else renderList(graphics, layout, mouseX, mouseY);
        renderFooter(graphics, layout);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        renderHoverTooltip(graphics, layout, mouseX, mouseY);
        renderPlayerDropdown(graphics, layout, mouseX, mouseY);
        moduleSwitcher.render(graphics, font, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, Layout layout) {
        int markX = layout.contentX();
        int markY = layout.panelY() + 8;
        RimeUi.roundedRect(graphics, markX, markY, 22, 22, 0xFF173247);
        RimeUi.roundedOutline(graphics, markX, markY, 22, 22, 0xFF38BDF8);
        graphics.centeredText(font, "+", markX + 11, markY + 7, RimeUi.ACCENT_HOVER);

        int headingWidth = Math.max(24, moduleSwitcher.left() - markX - 38);
        graphics.text(font, trimComponent(getHeading(), headingWidth), markX + 30,
                layout.panelY() + (layout.compact() ? 13 : 7), RimeUi.TEXT, false);
        if (!layout.compact()) {
            graphics.text(font, trimComponent(getSubtitle(), headingWidth), markX + 30,
                    layout.panelY() + 20, RimeUi.MUTED, false);
        }
        graphics.fill(layout.panelX() + 1, layout.headerBottom() - 1,
                layout.panelRight() - 1, layout.headerBottom(), RimeUi.BORDER_SOFT);
    }

    private void renderSearch(GuiGraphicsExtractor graphics, Layout layout) {
        RimeUi.roundedRect(graphics, layout.contentX(), layout.searchY(), layout.contentWidth(), layout.searchHeight(),
                searchField != null && searchField.isFocused() ? 0xFF202B35 : 0xFF181E26);
        RimeUi.roundedOutline(graphics, layout.contentX(), layout.searchY(), layout.contentWidth(), layout.searchHeight(),
                searchField != null && searchField.isFocused() ? RimeUi.ACCENT : RimeUi.BORDER);
    }

    private void renderList(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll(layout));
        RimeUi.roundedRect(graphics, layout.contentX(), layout.listTop(), layout.contentWidth(),
                layout.listHeight(), 0xA80D1117);
        RimeUi.roundedOutline(graphics, layout.contentX(), layout.listTop(), layout.contentWidth(),
                layout.listHeight(), RimeUi.BORDER_SOFT);

        int listRight = layout.contentRight() - SCROLL_BAR_WIDTH - 5;
        graphics.enableScissor(layout.contentX() + 1, layout.listTop() + 1,
                layout.contentRight() - 1, layout.listBottom() - 1);
        if (currentItemCount() == 0) {
            renderEmptyState(graphics, layout);
        } else if (currentTab == TAB_FAKE_PLAYERS) {
            List<FakePlayerInfo> entries = visibleFakePlayers();
            for (int i = 0; i < entries.size(); i++) {
                int entryY = layout.listTop() + 4 + i * ENTRY_HEIGHT - scrollOffset;
                if (entryY + ENTRY_HEIGHT <= layout.listTop() || entryY >= layout.listBottom()) continue;
                renderFakePlayerEntry(graphics, layout.contentX() + 4, entryY,
                        listRight - layout.contentX() - 4, entries.get(i), mouseX, mouseY);
            }
        } else {
            List<Waypoint> entries = visibleWaypoints();
            for (int i = 0; i < entries.size(); i++) {
                int entryY = layout.listTop() + 4 + i * ENTRY_HEIGHT - scrollOffset;
                if (entryY + ENTRY_HEIGHT <= layout.listTop() || entryY >= layout.listBottom()) continue;
                renderWaypointEntry(graphics, layout.contentX() + 4, entryY,
                        listRight - layout.contentX() - 4, entries.get(i), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        renderScrollBar(graphics, layout, currentItemCount());
    }

    private void renderPlayerPanel(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        RimeUi.roundedRect(graphics, layout.contentX(), layout.listTop(), layout.contentWidth(),
                layout.listHeight(), 0xA80D1117);
        RimeUi.roundedOutline(graphics, layout.contentX(), layout.listTop(), layout.contentWidth(),
                layout.listHeight(), RimeUi.BORDER_SOFT);
        TeleportPlayerTarget selected = selectedPlayer();
        int centerX = layout.contentX() + layout.contentWidth() / 2;
        int centerY = layout.listTop() + layout.listHeight() / 2
                - (data.canManageTpaAllowlist() ? 10 : 0);
        if (selected == null) {
            graphics.centeredText(font, Component.translatable("rime-tools.teleport.screen.players.select"),
                    centerX, centerY - 6, RimeUi.TEXT);
            graphics.centeredText(font, Component.translatable("rime-tools.teleport.screen.players.select_hint"),
                    centerX, centerY + 9, RimeUi.FAINT);
            return;
        }
        renderPlayerAvatar(graphics, selected.name(), centerX - 12, centerY - 30,
                selected.online() ? RimeUi.SUCCESS : RimeUi.FAINT);
        graphics.centeredText(font, selected.name(), centerX, centerY + 1, RimeUi.TEXT);
        graphics.centeredText(font, Component.translatable(selected.online()
                        ? "rime-tools.teleport.screen.players.online"
                        : "rime-tools.teleport.screen.players.offline"),
                centerX, centerY + 15, selected.online() ? RimeUi.SUCCESS : RimeUi.FAINT);
        if (data.canManageTpaAllowlist()) {
            graphics.centeredText(font, Component.translatable(selected.tpaAllowed()
                            ? "rime-tools.teleport.screen.players.allowed"
                            : "rime-tools.teleport.screen.players.not_allowed"),
                    centerX, centerY + 29, selected.tpaAllowed() ? RimeUi.SUCCESS : RimeUi.FAINT);
        }
    }

    private void renderPlayerDropdown(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        Dropdown dropdown = playerDropdown(layout);
        if (dropdown == null) return;
        List<TeleportPlayerTarget> players = filteredPlayerTargets();
        playerDropdownOffset = Math.clamp(playerDropdownOffset, 0,
                Math.max(0, players.size() - dropdown.visibleRows()));
        RimeUi.shadow(graphics, dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height());
        RimeUi.roundedRect(graphics, dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height(), RimeUi.PANEL);
        RimeUi.roundedOutline(graphics, dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height(), RimeUi.BORDER);
        if (players.isEmpty()) {
            graphics.centeredText(font, Component.translatable("rime-tools.teleport.screen.players.no_results"),
                    dropdown.x() + dropdown.width() / 2,
                    dropdown.y() + (dropdown.height() - font.lineHeight) / 2, RimeUi.MUTED);
            return;
        }
        int end = Math.min(players.size(), playerDropdownOffset + dropdown.visibleRows());
        for (int index = playerDropdownOffset; index < end; index++) {
            TeleportPlayerTarget player = players.get(index);
            int rowY = dropdown.y() + 4 + (index - playerDropdownOffset) * PLAYER_ROW_HEIGHT;
            boolean hovered = RimeUi.contains(mouseX, mouseY, dropdown.x() + 4, rowY,
                    dropdown.width() - 8, PLAYER_ROW_HEIGHT - 2);
            if (hovered || player.uuid().toString().equals(selectedPlayerId)) {
                RimeUi.roundedRect(graphics, dropdown.x() + 4, rowY, dropdown.width() - 8,
                        PLAYER_ROW_HEIGHT - 2, hovered ? RimeUi.SURFACE_HOVER : 0xFF173247);
            }
            Component state = Component.translatable(player.online()
                    ? "rime-tools.teleport.screen.players.online"
                    : "rime-tools.teleport.screen.players.offline");
            int stateWidth = font.width(state);
            graphics.text(font, trimComponent(Component.literal(player.name()),
                            Math.max(20, dropdown.width() - stateWidth - 28)),
                    dropdown.x() + 10, rowY + 4, RimeUi.TEXT, false);
            graphics.text(font, state, dropdown.x() + dropdown.width() - stateWidth - 10,
                    rowY + 4, player.online() ? RimeUi.SUCCESS : RimeUi.FAINT, false);
            graphics.text(font, trimToWidth(player.uuid().toString(),
                            Math.max(20, dropdown.width() - 90)),
                    dropdown.x() + 10, rowY + 16, RimeUi.FAINT, false);
            if (player.tpaAllowed()) {
                Component allowed = Component.translatable("rime-tools.teleport.screen.players.allowed");
                graphics.text(font, allowed, dropdown.x() + dropdown.width() - font.width(allowed) - 10,
                        rowY + 16, RimeUi.SUCCESS, false);
            }
        }
        int max = Math.max(0, players.size() - dropdown.visibleRows());
        if (max > 0) {
            int trackX = dropdown.x() + dropdown.width() - 5;
            int trackHeight = dropdown.height() - 8;
            int thumbHeight = Math.max(10, trackHeight * dropdown.visibleRows() / players.size());
            int thumbY = dropdown.y() + 4 + playerDropdownOffset * (trackHeight - thumbHeight) / max;
            RimeUi.roundedRect(graphics, trackX, thumbY, 2, thumbHeight, RimeUi.ACCENT);
        }
    }

    private void renderEmptyState(GuiGraphicsExtractor graphics, Layout layout) {
        String titleKey = !normalizedSearch().isEmpty()
                ? "rime-tools.teleport.screen.waypoints.no_results"
                : currentTab == TAB_FAKE_PLAYERS
                  ? "rime-tools.teleport.screen.waypoints.no_fake_players"
                  : "rime-tools.teleport.screen.waypoints.empty";
        String hintKey = !normalizedSearch().isEmpty()
                ? "rime-tools.teleport.screen.waypoints.no_results_hint"
                : currentTab == TAB_FAKE_PLAYERS
                  ? "rime-tools.teleport.screen.waypoints.no_fake_players_hint"
                  : "rime-tools.teleport.screen.waypoints.empty_hint";
        int centerY = layout.listTop() + layout.listHeight() / 2;
        graphics.centeredText(font, Component.translatable(titleKey),
                layout.panelX() + layout.panelWidth() / 2, centerY - 11, RimeUi.TEXT);
        graphics.centeredText(font, Component.translatable(hintKey),
                layout.panelX() + layout.panelWidth() / 2, centerY + 3, RimeUi.FAINT);
    }

    private void renderWaypointEntry(GuiGraphicsExtractor graphics, int x, int y, int entryWidth,
                                     Waypoint waypoint, int mouseX, int mouseY) {
        boolean hovered = RimeUi.contains(mouseX, mouseY, x, y, entryWidth, ENTRY_HEIGHT - 4);
        int accent = currentTab == TAB_PERSONAL ? RimeUi.ACCENT : RimeUi.BLUE;
        renderEntryBody(graphics, x, y, entryWidth, hovered, accent);

        boolean canManage = canManage(waypoint);
        int actionCount = canManage ? 3 : 1;
        int actionWidth = actionCount * ACTION_SIZE + (actionCount - 1) * ACTION_GAP;
        int actionX = x + entryWidth - actionWidth - 8;
        int actionY = y + 7;
        int markerX = x + 7;
        int textX = x + 39;

        boolean hasAlias = waypoint.getAlias() != null && !waypoint.getAlias().isBlank();
        String primary = hasAlias ? waypoint.getAlias() : waypoint.getName();
        String coordinates = coordinateValues(waypoint.getX(), waypoint.getY(), waypoint.getZ());
        int coordinateX = coordinateX(coordinates, textX, actionX);
        int textRight = coordinateX - 10;

        renderWaypointMarker(graphics, markerX, y + 6, firstCodePoint(primary), accent);
        graphics.text(font, trimToWidth(primary, Math.max(32, textRight - textX)),
                textX, y + 6, RimeUi.TEXT, false);
        String identity = hasAlias
                ? Component.translatable("rime-tools.teleport.screen.waypoints.id", waypoint.getName()).getString()
                : null;
        renderMetadata(graphics, textX, y + 19, textRight, identity,
                displayWorld(waypoint.getWorld()), accent);
        renderCoordinates(graphics, coordinates, coordinateX, y + 14, actionX - 8);

        drawAction(graphics, actionX, actionY, "\u2192", RimeUi.BLUE,
                RimeUi.contains(mouseX, mouseY, actionX, actionY, ACTION_SIZE, ACTION_SIZE));
        if (canManage) {
            int editX = actionX + ACTION_SIZE + ACTION_GAP;
            int deleteX = editX + ACTION_SIZE + ACTION_GAP;
            drawAction(graphics, editX, actionY, "\u270E", RimeUi.ACCENT,
                    RimeUi.contains(mouseX, mouseY, editX, actionY, ACTION_SIZE, ACTION_SIZE));
            boolean confirming = waypoint.getName().equals(pendingDeleteName);
            drawAction(graphics, deleteX, actionY, confirming ? "?" : "\u00D7", RimeUi.DANGER,
                    RimeUi.contains(mouseX, mouseY, deleteX, actionY, ACTION_SIZE, ACTION_SIZE));
        }
    }

    private void renderFakePlayerEntry(GuiGraphicsExtractor graphics, int x, int y, int entryWidth,
                                       FakePlayerInfo fakePlayer, int mouseX, int mouseY) {
        boolean hovered = RimeUi.contains(mouseX, mouseY, x, y, entryWidth, ENTRY_HEIGHT - 4);
        int accent = fakePlayer.ownedByViewer() ? RimeUi.SUCCESS : RimeUi.FAINT;
        renderEntryBody(graphics, x, y, entryWidth, hovered, accent);

        int actionX = x + entryWidth - ACTION_SIZE - 8;
        int actionY = y + 7;
        int textX = x + 39;
        int textRight = actionX - 8;
        renderPlayerAvatar(graphics, fakePlayer.name(), x + 7, y + 6, accent);
        graphics.text(font, trimToWidth(fakePlayer.name(), Math.max(32, textRight - textX)),
                textX, y + 6, fakePlayer.ownedByViewer() ? RimeUi.TEXT : RimeUi.MUTED, false);
        String creator = fakePlayer.creatorName() == null ? "?" : fakePlayer.creatorName();
        renderMetadata(graphics, textX, y + 19, textRight, creator,
                displayWorld(fakePlayer.world()), accent);

        drawAction(graphics, actionX, actionY,
                fakePlayer.ownedByViewer() ? "\u2192" : "\u2212",
                fakePlayer.ownedByViewer() ? RimeUi.SUCCESS : RimeUi.FAINT,
                RimeUi.contains(mouseX, mouseY, actionX, actionY, ACTION_SIZE, ACTION_SIZE));
    }

    private void renderEntryBody(GuiGraphicsExtractor graphics, int x, int y, int width,
                                 boolean hovered, int accent) {
        RimeUi.roundedRect(graphics, x, y, width, ENTRY_HEIGHT - 4,
                hovered ? RimeUi.SURFACE_HOVER : 0x75171D25);
        if (hovered) {
            RimeUi.roundedOutline(graphics, x, y, width, ENTRY_HEIGHT - 4, accent);
        } else {
            graphics.fill(x + 8, y + ENTRY_HEIGHT - 5, x + width - 8,
                    y + ENTRY_HEIGHT - 4, RimeUi.BORDER_SOFT);
        }
    }

    private void drawAction(GuiGraphicsExtractor graphics, int x, int y, String icon, int color, boolean hovered) {
        RimeUi.roundedRect(graphics, x, y, ACTION_SIZE, ACTION_SIZE,
                hovered ? 0xFF303A47 : 0x801A212A);
        if (hovered) RimeUi.roundedOutline(graphics, x, y, ACTION_SIZE, ACTION_SIZE, color);
        graphics.centeredText(font, icon, x + ACTION_SIZE / 2,
                y + (ACTION_SIZE - font.lineHeight) / 2 + 1, hovered ? color : RimeUi.MUTED);
    }

    private void renderWaypointMarker(GuiGraphicsExtractor graphics, int x, int y,
                                      String initial, int accent) {
        int background = accent == RimeUi.ACCENT ? 0xFF173247 : 0xFF1D334B;
        RimeUi.roundedRect(graphics, x, y, 24, 24, background);
        RimeUi.roundedOutline(graphics, x, y, 24, 24, accent);
        graphics.centeredText(font, initial, x + 12, y + 8, accent);
    }

    private void renderMetadata(GuiGraphicsExtractor graphics, int x, int y, int right,
                                String identity, String world, int accent) {
        int available = Math.max(0, right - x);
        if (available == 0) return;

        int cursor = x;
        if (identity != null && !identity.isBlank()) {
            String visibleIdentity = trimToWidth(identity, Math.max(20, Math.min(available / 3, 72)));
            graphics.text(font, visibleIdentity, cursor, y, accent, false);
            cursor += font.width(visibleIdentity) + 7;
        }

        int worldWidth = Math.max(0, right - cursor);
        if (worldWidth > 0) {
            String visibleWorld = trimToWidth(world, worldWidth);
            graphics.text(font, visibleWorld, cursor, y, RimeUi.MUTED, false);
        }
    }

    private int coordinateX(String coordinates, int textX, int actionX) {
        int available = Math.max(1, actionX - textX - 8);
        int budget = Math.max(30, Math.min(90, available / 3));
        String visible = trimToWidth(coordinates, budget);
        return actionX - 8 - font.width(visible);
    }

    private void renderCoordinates(GuiGraphicsExtractor graphics, String coordinates,
                                   int x, int y, int right) {
        graphics.text(font, trimToWidth(coordinates, Math.max(1, right - x)),
                x, y, RimeUi.FAINT, false);
    }

    private void renderScrollBar(GuiGraphicsExtractor graphics, Layout layout, int itemCount) {
        int maxScroll = maxScroll(layout);
        if (maxScroll <= 0) return;
        int trackX = layout.contentRight() - SCROLL_BAR_WIDTH - 3;
        int trackY = layout.listTop() + 5;
        int trackHeight = Math.max(1, layout.listHeight() - 10);
        graphics.fill(trackX, trackY, trackX + SCROLL_BAR_WIDTH, trackY + trackHeight, 0xFF202832);
        int contentHeight = itemCount * ENTRY_HEIGHT + 8;
        int thumbHeight = Math.min(trackHeight,
                Math.max(Math.min(18, trackHeight), trackHeight * layout.listHeight() / contentHeight));
        int thumbY = trackY + (int) ((long) scrollOffset * Math.max(0, trackHeight - thumbHeight) / maxScroll);
        RimeUi.roundedRect(graphics, trackX, thumbY, SCROLL_BAR_WIDTH, thumbHeight,
                draggingScrollBar ? RimeUi.ACCENT_HOVER : RimeUi.ACCENT);
    }

    private void renderFooter(GuiGraphicsExtractor graphics, Layout layout) {
        graphics.fill(layout.panelX() + 1, layout.footerY(), layout.panelRight() - 1,
                layout.footerY() + 1, RimeUi.BORDER_SOFT);
        if (currentTab == TAB_PLAYERS) return;
        Component count;
        if (!normalizedSearch().isEmpty()) {
            count = Component.translatable("rime-tools.teleport.screen.waypoints.search_count",
                    currentItemCount(), currentTotalCount());
        } else if (currentTab == TAB_FAKE_PLAYERS) {
            count = Component.translatable("rime-tools.teleport.screen.waypoints.fake_count", fakePlayers.size());
        } else {
            int max = currentTab == TAB_PERSONAL ? 10 : 100;
            count = Component.translatable("rime-tools.teleport.screen.waypoints.count", currentItemCount(), max);
        }
        int countWidth = currentTab == TAB_FAKE_PLAYERS
                ? layout.contentWidth()
                : Math.max(24, layout.contentWidth() - Math.min(108, layout.contentWidth()) - 8);
        Component visibleCount = trimComponent(count, countWidth);
        graphics.text(font, visibleCount, layout.contentRight() - font.width(visibleCount),
                layout.footerY() + (layout.footerHeight() - font.lineHeight) / 2 + 1,
                RimeUi.MUTED, false);
    }

    private void renderHoverTooltip(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        if (currentTab == TAB_PLAYERS) return;
        if (currentTab == TAB_FAKE_PLAYERS) {
            FakePlayerHit hit = fakePlayerAt(layout, mouseX, mouseY);
            if (hit == null) return;
            if (isOverSingleAction(hit.x(), hit.y(), hit.width(), mouseX, mouseY)) {
                Component tooltip = hit.fakePlayer().ownedByViewer()
                        ? Component.translatable("rime-tools.teleport.screen.waypoints.teleport_fake")
                        : Component.translatable("rime-tools.teleport.screen.waypoints.fake_locked");
                graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
            } else {
                String key = hit.fakePlayer().creatorName() == null
                        ? "rime-tools.teleport.screen.waypoints.fake_creator_unknown"
                        : "rime-tools.teleport.screen.waypoints.fake_creator";
                Component creator = hit.fakePlayer().creatorName() == null
                        ? Component.translatable(key)
                        : Component.translatable(key, hit.fakePlayer().creatorName());
                graphics.setTooltipForNextFrame(creator, mouseX, mouseY);
            }
            return;
        }

        WaypointHit hit = waypointAt(layout, mouseX, mouseY);
        if (hit == null) return;
        int action = actionAt(hit, mouseX, mouseY);
        if (action == 0) {
            graphics.setTooltipForNextFrame(Component.translatable("rime-tools.teleport.screen.waypoints.teleport"), mouseX, mouseY);
        } else if (action == 1) {
            graphics.setTooltipForNextFrame(Component.translatable("rime-tools.teleport.screen.waypoints.edit"), mouseX, mouseY);
        } else if (action == 2) {
            String key = hit.waypoint().getName().equals(pendingDeleteName)
                    ? "rime-tools.teleport.screen.waypoints.delete_confirm"
                    : "rime-tools.teleport.screen.waypoints.delete";
            graphics.setTooltipForNextFrame(Component.translatable(key), mouseX, mouseY);
        } else if (hit.waypoint().getDescription() != null && !hit.waypoint().getDescription().isBlank()) {
            var lines = new ArrayList<>(font.split(Component.literal(hit.waypoint().getDescription()),
                    Math.min(260, width - 40)));
            if (canManage(hit.waypoint())) {
                lines.addAll(font.split(Component.translatable("rime-tools.teleport.screen.waypoints.right_click_edit"),
                        Math.min(260, width - 40)));
            }
            graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean mouseOver) {
        if (moduleSwitcher.mouseClicked(event)) return true;
        Layout layout = layout();
        TeleportPlayerTarget playerHit = playerTargetAt(layout, event.x(), event.y());
        if (playerHit != null && event.button() == 0) {
            selectedPlayerId = playerHit.uuid().toString();
            searchQuery = playerHit.name();
            searchField.setValue(playerHit.name());
            playerDropdownOpen = false;
            updatePlayerActions();
            return true;
        }
        Dropdown dropdown = playerDropdown(layout);
        if (dropdown != null && event.button() == 0 && RimeUi.contains(event.x(), event.y(),
                dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height())) return true;
        if (super.mouseClicked(event, mouseOver)) return true;
        if (event.button() == 0 && RimeUi.contains(event.x(), event.y(), layout.contentX(), layout.searchY(),
                layout.contentWidth(), layout.searchHeight())) {
            setFocused(searchField);
            if (currentTab == TAB_PLAYERS) playerDropdownOpen = true;
            return true;
        }
        if (currentTab == TAB_PLAYERS) {
            if (playerDropdownOpen && event.button() == 0) playerDropdownOpen = false;
            return false;
        }
        if (event.button() == 0 && isOverScrollBar(layout, event.x(), event.y())) {
            draggingScrollBar = true;
            updateScrollFromMouse(layout, event.y());
            return true;
        }

        if (currentTab == TAB_FAKE_PLAYERS) {
            FakePlayerHit hit = fakePlayerAt(layout, event.x(), event.y());
            if (hit == null || event.button() != 0) return false;
            if (hit.fakePlayer().ownedByViewer()
                    && isOverSingleAction(hit.x(), hit.y(), hit.width(), event.x(), event.y())) {
                teleportToFakePlayer(hit.fakePlayer());
                return true;
            }
            return false;
        }

        WaypointHit hit = waypointAt(layout, event.x(), event.y());
        if (hit == null) {
            pendingDeleteName = null;
            return false;
        }
        if (event.button() == 1 && canManage(hit.waypoint())) {
            editDetails(hit.waypoint());
            return true;
        }
        if (event.button() != 0) return false;

        int action = actionAt(hit, event.x(), event.y());
        if (action == 0) {
            teleportTo(hit.waypoint());
            return true;
        }
        if (action == 1) {
            editDetails(hit.waypoint());
            return true;
        }
        if (action == 2) {
            if (hit.waypoint().getName().equals(pendingDeleteName)) {
                deleteWaypoint(hit.waypoint());
                pendingDeleteName = null;
            } else {
                pendingDeleteName = hit.waypoint().getName();
            }
            return true;
        }
        pendingDeleteName = null;
        return false;
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
        if (currentTab == TAB_PLAYERS) return false;
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
        return super.keyPressed(event);
    }

    @Override
    public void resize(int width, int height) {
        if (searchField != null) searchQuery = searchField.getValue();
        super.resize(width, height);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll(layout()));
    }

    private void updateScrollFromMouse(Layout layout, double mouseY) {
        int maxScroll = maxScroll(layout);
        if (maxScroll <= 0) return;
        int trackY = layout.listTop() + 5;
        int trackHeight = Math.max(1, layout.listHeight() - 10);
        int contentHeight = currentItemCount() * ENTRY_HEIGHT + 8;
        int thumbHeight = Math.min(trackHeight,
                Math.max(Math.min(18, trackHeight), trackHeight * layout.listHeight() / contentHeight));
        double ratio = (mouseY - trackY - thumbHeight / 2.0)
                / Math.max(1, trackHeight - thumbHeight);
        scrollOffset = Math.clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
    }

    private boolean isOverScrollBar(Layout layout, double mouseX, double mouseY) {
        return maxScroll(layout) > 0 && RimeUi.contains(mouseX, mouseY,
                layout.contentRight() - 10, layout.listTop(), 10, layout.listHeight());
    }

    private WaypointHit waypointAt(Layout layout, double mouseX, double mouseY) {
        if (!RimeUi.contains(mouseX, mouseY, layout.contentX() + 4, layout.listTop() + 1,
                layout.contentWidth() - 13, layout.listHeight() - 2)) return null;
        int index = (int) (mouseY - layout.listTop() - 4 + scrollOffset) / ENTRY_HEIGHT;
        List<Waypoint> entries = visibleWaypoints();
        if (index < 0 || index >= entries.size()) return null;
        int entryY = layout.listTop() + 4 + index * ENTRY_HEIGHT - scrollOffset;
        if (mouseY >= entryY + ENTRY_HEIGHT - 4) return null;
        return new WaypointHit(entries.get(index), layout.contentX() + 4, entryY,
                layout.contentWidth() - 13);
    }

    private FakePlayerHit fakePlayerAt(Layout layout, double mouseX, double mouseY) {
        if (!RimeUi.contains(mouseX, mouseY, layout.contentX() + 4, layout.listTop() + 1,
                layout.contentWidth() - 13, layout.listHeight() - 2)) return null;
        int index = (int) (mouseY - layout.listTop() - 4 + scrollOffset) / ENTRY_HEIGHT;
        List<FakePlayerInfo> entries = visibleFakePlayers();
        if (index < 0 || index >= entries.size()) return null;
        int entryY = layout.listTop() + 4 + index * ENTRY_HEIGHT - scrollOffset;
        if (mouseY >= entryY + ENTRY_HEIGHT - 4) return null;
        return new FakePlayerHit(entries.get(index), layout.contentX() + 4, entryY,
                layout.contentWidth() - 13);
    }

    private int actionAt(WaypointHit hit, double mouseX, double mouseY) {
        boolean canManage = canManage(hit.waypoint());
        int count = canManage ? 3 : 1;
        int actionX = hit.x() + hit.width() - (count * ACTION_SIZE + (count - 1) * ACTION_GAP) - 8;
        int actionY = hit.y() + 7;
        for (int i = 0; i < count; i++) {
            int x = actionX + i * (ACTION_SIZE + ACTION_GAP);
            if (RimeUi.contains(mouseX, mouseY, x, actionY, ACTION_SIZE, ACTION_SIZE)) return i;
        }
        return -1;
    }

    private boolean isOverSingleAction(int entryX, int entryY, int entryWidth, double mouseX, double mouseY) {
        return RimeUi.contains(mouseX, mouseY, entryX + entryWidth - ACTION_SIZE - 8,
                entryY + 7, ACTION_SIZE, ACTION_SIZE);
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, currentItemCount() * ENTRY_HEIGHT + 8 - layout.listHeight());
    }

    private boolean canManage(Waypoint waypoint) {
        if (data.mode() == WaypointActionPayload.MODE_OTHER_READ_ONLY) return false;
        boolean isOwner = waypoint.getOwner() != null && waypoint.getOwner().equals(getViewerUuid());
        return data.mode() == WaypointActionPayload.MODE_ADMIN || isOwner
                || (currentTab == TAB_PERSONAL && data.mode() == WaypointActionPayload.MODE_OWN);
    }

    private List<Waypoint> visibleWaypoints() {
        ensureFilterCache();
        return cachedWaypoints;
    }

    private List<FakePlayerInfo> visibleFakePlayers() {
        ensureFilterCache();
        return cachedFakePlayers;
    }

    private List<TeleportPlayerTarget> filteredPlayerTargets() {
        ensureFilterCache();
        return cachedPlayerTargets;
    }

    private void ensureFilterCache() {
        String query = normalizedSearch();
        if (filteredCacheTab == currentTab && query.equals(filteredCacheQuery)) return;
        filteredCacheTab = currentTab;
        filteredCacheQuery = query;

        List<Waypoint> waypointSource = currentTab == TAB_PERSONAL ? personalWaypoints : globalWaypoints;
        cachedWaypoints = query.isEmpty() ? waypointSource : waypointSource.stream()
                                                             .filter(waypoint -> matches(query, waypoint.getName(), waypoint.getAlias(),
                                                                     waypoint.getDescription(), waypoint.getWorld())).toList();
        cachedFakePlayers = query.isEmpty() ? fakePlayers : fakePlayers.stream()
                                                            .filter(fakePlayer -> matches(query, fakePlayer.name(), fakePlayer.creatorName(),
                                                                    fakePlayer.world())).toList();
        cachedPlayerTargets = query.isEmpty() ? playerTargets : playerTargets.stream()
                                                                .filter(player -> player.name().toLowerCase(Locale.ROOT).contains(query)
                                                                                  || player.uuid().toString().contains(query)).toList();
    }

    private void invalidateFilterCache() {
        filteredCacheTab = -1;
        filteredCacheQuery = null;
    }

    private TeleportPlayerTarget selectedPlayer() {
        if (selectedPlayerId == null) return null;
        return playerTargets.stream().filter(player -> player.uuid().toString().equals(selectedPlayerId))
                .findFirst().orElse(null);
    }

    private Dropdown playerDropdown(Layout layout) {
        if (currentTab != TAB_PLAYERS || !playerDropdownOpen) return null;
        int y = layout.searchY() + layout.searchHeight() + 3;
        int availableHeight = Math.max(PLAYER_ROW_HEIGHT + 8, layout.listBottom() - y);
        int rows = Math.max(1, Math.min(5, (availableHeight - 8) / PLAYER_ROW_HEIGHT));
        int visible = Math.max(1, Math.min(rows, filteredPlayerTargets().size()));
        return new Dropdown(layout.contentX(), y, layout.contentWidth(), visible * PLAYER_ROW_HEIGHT + 8, visible);
    }

    private TeleportPlayerTarget playerTargetAt(Layout layout, double mouseX, double mouseY) {
        Dropdown dropdown = playerDropdown(layout);
        if (dropdown == null || !RimeUi.contains(mouseX, mouseY,
                dropdown.x() + 4, dropdown.y() + 4, dropdown.width() - 8, dropdown.height() - 8)) return null;
        int row = (int) (mouseY - dropdown.y() - 4) / PLAYER_ROW_HEIGHT;
        List<TeleportPlayerTarget> players = filteredPlayerTargets();
        int index = playerDropdownOffset + row;
        return index >= 0 && index < players.size() ? players.get(index) : null;
    }

    private void updatePlayerActions() {
        TeleportPlayerTarget selected = selectedPlayer();
        boolean hasTarget = selected != null;
        if (tpaButton != null) tpaButton.active = hasTarget && selected.online() && data.canTpa();
        if (tpahereButton != null) tpahereButton.active = hasTarget && selected.online() && data.canTpahere();
        if (lastButton != null) lastButton.active = hasTarget && data.canLast();
        if (privateButton != null) privateButton.active = hasTarget && data.canOtherPersonal();
        if (allowlistButton != null) {
            allowlistButton.active = hasTarget && data.canManageTpaAllowlist();
            allowlistButton.setMessage(Component.translatable(hasTarget && selected.tpaAllowed()
                    ? "rime-tools.teleport.screen.players.remove_direct"
                    : "rime-tools.teleport.screen.players.allow_direct"));
        }
    }

    private void toggleAllowlist() {
        TeleportPlayerTarget selected = selectedPlayer();
        if (selected == null || !data.canManageTpaAllowlist()) return;
        ClientPlayNetworking.send(new TpaAllowlistActionPayload(
                selected.uuid(), !selected.tpaAllowed()));
        sendAction(WaypointActionPayload.ACTION_REFRESH,
                WaypointActionPayload.SCOPE_PERSONAL, null, null, null, false);
        if (allowlistButton != null) allowlistButton.active = false;
        playerDropdownOpen = false;
    }

    private void runPlayerCommand(String subcommand, boolean requiresOnline) {
        TeleportPlayerTarget selected = selectedPlayer();
        if (selected == null || requiresOnline && !selected.online() || minecraft == null || minecraft.player == null)
            return;
        String target = requiresOnline ? selected.name() : selected.uuid().toString();
        minecraft.player.connection.sendCommand("rime " + subcommand + " " + target);
        playerDropdownOpen = false;
    }

    private int currentItemCount() {
        return switch (currentTab) {
            case TAB_PLAYERS -> filteredPlayerTargets().size();
            case TAB_FAKE_PLAYERS -> visibleFakePlayers().size();
            default -> visibleWaypoints().size();
        };
    }

    private int currentTotalCount() {
        return switch (currentTab) {
            case TAB_PERSONAL -> personalWaypoints.size();
            case TAB_GLOBAL -> globalWaypoints.size();
            case TAB_PLAYERS -> playerTargets.size();
            default -> fakePlayers.size();
        };
    }

    private String normalizedSearch() {
        return searchQuery.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private Component trimComponent(Component text, int maxWidth) {
        return Component.literal(trimToWidth(text.getString(), maxWidth));
    }

    private int centeredTextY(int fieldY, int fieldHeight) {
        return fieldY + (fieldHeight - font.lineHeight) / 2 + 1;
    }

    private void teleportTo(Waypoint waypoint) {
        if (data.mode() == WaypointActionPayload.MODE_OTHER_READ_ONLY && data.targetUuid() != null
                && minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand("rime tpother " + data.targetUuid() + " " + waypoint.getName());
            return;
        }
        sendAction(WaypointActionPayload.ACTION_TELEPORT, currentTab,
                waypoint.getName(), null, null, false);
    }

    private void teleportToFakePlayer(FakePlayerInfo fakePlayer) {
        sendAction(WaypointActionPayload.ACTION_TELEPORT_FAKE, WaypointActionPayload.SCOPE_FAKE_PLAYER,
                fakePlayer.name(), null, null, false);
    }

    private void deleteWaypoint(Waypoint waypoint) {
        sendAction(WaypointActionPayload.ACTION_DELETE, currentTab,
                waypoint.getName(), null, null, false);
    }

    private void editDetails(Waypoint waypoint) {
        if (minecraft != null) minecraft.setScreenAndShow(new WaypointEditScreen(this, currentTab, waypoint));
    }

    private void createWaypoint() {
        if (minecraft != null) minecraft.setScreenAndShow(new WaypointEditScreen(this, currentTab, null));
    }

    void sendAction(int action, int scope, String name, String alias, String description, boolean overwrite) {
        ClientPlayNetworking.send(new WaypointActionPayload(
                action, scope, data.mode(), data.targetUuid(), name, alias, description, overwrite));
    }

    public void refreshData(OpenWaypointScreenPayload newData) {
        String selectedId = selectedPlayerId;
        setData(newData);
        if (selectedId != null && playerTargets.stream()
                .anyMatch(player -> player.uuid().toString().equals(selectedId))) {
            selectedPlayerId = selectedId;
        } else {
            selectedPlayerId = null;
        }
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll(layout()));
        playerDropdownOffset = Math.clamp(playerDropdownOffset, 0,
                Math.max(0, filteredPlayerTargets().size() - 1));
        clearWidgets();
        init();
    }

    private Component getHeading() {
        if ((data.mode() == WaypointActionPayload.MODE_ADMIN
                || data.mode() == WaypointActionPayload.MODE_OTHER_READ_ONLY) && data.targetName() != null) {
            return Component.translatable(data.mode() == WaypointActionPayload.MODE_ADMIN
                    ? "rime-tools.teleport.screen.waypoints.manage"
                    : "rime-tools.teleport.screen.waypoints.view_other", data.targetName());
        }
        return Component.translatable("rime-tools.teleport.screen.waypoints.title");
    }

    private Component getSubtitle() {
        return Component.translatable(switch (currentTab) {
            case TAB_PERSONAL -> "rime-tools.teleport.screen.waypoints.personal_subtitle";
            case TAB_GLOBAL -> "rime-tools.teleport.screen.waypoints.global_subtitle";
            case TAB_PLAYERS -> "rime-tools.teleport.screen.waypoints.players_subtitle";
            default -> "rime-tools.teleport.screen.waypoints.fake_players_subtitle";
        });
    }

    private UUID getViewerUuid() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
    }

    private Layout layout() {
        int horizontalMargin = Math.clamp(width / 32, 4, 12);
        int verticalMargin = Math.clamp(height / 32, 4, 12);
        int panelWidth = Math.max(1, Math.min(440, width - horizontalMargin * 2));
        int panelHeight = Math.max(1, Math.min(320, height - verticalMargin * 2));
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        boolean compact = panelHeight < 230;
        int contentInset = panelWidth < 280 ? 8 : 12;
        int contentX = panelX + contentInset;
        int contentWidth = Math.max(1, panelWidth - contentInset * 2);
        int headerHeight = compact ? 36 : 42;
        int searchHeight = compact ? 22 : 24;
        int tabHeight = compact ? 20 : 24;
        int footerHeight = compact ? 32 : 39;
        int searchY = panelY + headerHeight + (compact ? 4 : 6);
        int tabsY = searchY + searchHeight + 5;
        int listTop = tabsY + tabHeight + 5;
        int footerY = panelY + panelHeight - footerHeight;
        return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentWidth,
                compact, panelY + headerHeight, searchY, searchHeight, tabsY, tabHeight,
                listTop, footerY, footerHeight);
    }

    private record WaypointHit(Waypoint waypoint, int x, int y, int width) {
    }

    private record FakePlayerHit(FakePlayerInfo fakePlayer, int x, int y, int width) {
    }

    private record Dropdown(int x, int y, int width, int height, int visibleRows) {
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, boolean compact, int headerBottom,
                          int searchY, int searchHeight, int tabsY, int tabHeight,
                          int listTop, int footerY, int footerHeight) {
        int panelRight() {
            return panelX + panelWidth;
        }

        int contentRight() {
            return contentX + contentWidth;
        }

        int listBottom() {
            return Math.max(listTop + 1, footerY - (compact ? 4 : 6));
        }

        int listHeight() {
            return Math.max(1, listBottom() - listTop);
        }

        int footerButtonHeight() {
            return compact ? 22 : 24;
        }

        int footerButtonOffset() {
            return (footerHeight - footerButtonHeight()) / 2;
        }
    }
}
