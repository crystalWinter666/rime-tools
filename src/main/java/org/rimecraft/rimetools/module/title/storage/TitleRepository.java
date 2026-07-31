package org.rimecraft.rimetools.module.title.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.title.config.TitleConfig;
import org.rimecraft.rimetools.module.title.permission.PermissionChecker;
import org.rimecraft.rimetools.module.title.permission.TitlePermissions;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class TitleRepository {
    private final TitleState state;
    private final TitleConfig config;

    private TitleRepository(TitleState state, TitleConfig config) {
        this.state = state;
        this.config = config;
    }

    public static TitleRepository from(ServerLevel level, TitleConfig config) {
        return new TitleRepository(level.getDataStorage().computeIfAbsent(TitleState.TYPE), config);
    }

    public TitleState state() {
        return state;
    }

    public Optional<TitleDefinition> findVisibleTitle(ServerPlayer player, PermissionChecker permissions) {
        String selected = state.selection(player.getUUID());
        if (selected != null) {
            TitleDefinition title = state.title(selected);
            if (title != null && title.enabled() && permissions.has(player, TitlePermissions.title(selected))) {
                return Optional.of(title);
            }
            state.clearSelection(player.getUUID());
        }
        return Optional.empty();
    }

    public Component fallbackComponent() {
        return Component.literal(config.defaultTitle())
                .withStyle(style -> style.withColor(TextColor.parseColor(config.defaultColor()).getOrThrow()));
    }

    public Optional<TitleDefinition> highestEnabledTitle() {
        return state.titles().values().stream()
                .filter(TitleDefinition::enabled)
                .max(Comparator.comparingInt(TitleDefinition::weight));
    }

    public void select(UUID player, String titleId) {
        TitleDefinition title = state.title(titleId);
        if (title == null || !title.enabled()) {
            throw new IllegalArgumentException("Unknown or disabled title: " + titleId);
        }
        state.select(player, titleId);
    }

    public void put(TitleDefinition title) {
        state.putTitle(title);
        if (!title.enabled()) {
            state.clearSelectionsFor(title.id());
        }
    }

    public boolean remove(String titleId) {
        if (!state.containsTitle(titleId)) {
            return false;
        }
        state.removeTitle(titleId);
        return true;
    }
}
