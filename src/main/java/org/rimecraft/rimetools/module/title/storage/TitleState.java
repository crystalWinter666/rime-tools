package org.rimecraft.rimetools.module.title.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;
import org.rimecraft.rimetools.module.title.title.TitleSelection;

import java.util.*;

public final class TitleState extends SavedData {
    public static final Codec<TitleState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TitleDefinition.CODEC.listOf().fieldOf("titles").forGetter(state -> state.titles.values().stream().toList()),
            TitleSelection.CODEC.listOf().fieldOf("selections").forGetter(state -> state.selections.entrySet().stream()
                    .map(entry -> new TitleSelection(entry.getKey(), entry.getValue())).toList()),
            Codec.STRING.optionalFieldOf("last_weekly_settlement", "").forGetter(state -> state.lastWeeklySettlement),
            WeeklyAwardGrant.CODEC.listOf().optionalFieldOf("weekly_awards", java.util.List.of())
                    .forGetter(TitleState::weeklyAwardGrants),
            Codec.STRING.optionalFieldOf("last_monthly_settlement", "").forGetter(state -> state.lastMonthlySettlement)
    ).apply(instance, TitleState::fromCodec));

    public static final SavedDataType<TitleState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "titles"),
            TitleState::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<String, TitleDefinition> titles = new HashMap<>();
    private final Map<UUID, String> selections = new HashMap<>();
    private final Map<UUID, Set<String>> weeklyAwards = new HashMap<>();
    private String lastWeeklySettlement = "";
    private String lastMonthlySettlement = "";

    public TitleState() {
    }

    private static TitleState fromCodec(java.util.List<TitleDefinition> titles,
                                        java.util.List<TitleSelection> selections,
                                        String lastWeeklySettlement,
                                        java.util.List<WeeklyAwardGrant> weeklyAwards,
                                        String lastMonthlySettlement) {
        TitleState state = new TitleState();
        titles.forEach(title -> state.titles.put(title.id(), title));
        selections.forEach(selection -> state.selections.put(selection.player(), selection.titleId()));
        state.lastWeeklySettlement = lastWeeklySettlement;
        weeklyAwards.forEach(grant -> state.weeklyAwards.put(grant.playerId(), new HashSet<>(grant.titleIds())));
        state.lastMonthlySettlement = lastMonthlySettlement;
        return state;
    }

    public Map<String, TitleDefinition> titles() {
        return Map.copyOf(titles);
    }

    public Map<UUID, String> selections() {
        return Map.copyOf(selections);
    }

    public TitleDefinition title(String id) {
        return titles.get(id);
    }

    public String selection(UUID player) {
        return selections.get(player);
    }

    public boolean containsTitle(String id) {
        return titles.containsKey(id);
    }

    public String lastWeeklySettlement() {
        return lastWeeklySettlement;
    }

    public String lastMonthlySettlement() {
        return lastMonthlySettlement;
    }

    public Map<UUID, Set<String>> weeklyAwards() {
        Map<UUID, Set<String>> copy = new HashMap<>();
        weeklyAwards.forEach((player, titles) -> copy.put(player, Set.copyOf(titles)));
        return Map.copyOf(copy);
    }

    public void completeWeeklySettlement(String settlementDate, Map<UUID, Set<String>> grants) {
        lastWeeklySettlement = settlementDate;
        weeklyAwards.clear();
        grants.forEach((player, titles) -> weeklyAwards.put(player, new HashSet<>(titles)));
        setDirty();
    }

    public void completeMonthlySettlement(String settlementDate) {
        lastMonthlySettlement = settlementDate;
        setDirty();
    }

    public void putTitle(TitleDefinition title) {
        titles.put(title.id(), title);
        setDirty();
    }

    public void removeTitle(String id) {
        titles.remove(id);
        clearSelectionsFor(id);
        setDirty();
    }

    public void clearSelectionsFor(String titleId) {
        if (selections.values().removeIf(titleId::equals)) {
            setDirty();
        }
    }

    public void select(UUID player, String titleId) {
        selections.put(player, titleId);
        setDirty();
    }

    public void clearSelection(UUID player) {
        if (selections.remove(player) != null) {
            setDirty();
        }
    }

    private java.util.List<WeeklyAwardGrant> weeklyAwardGrants() {
        return weeklyAwards.entrySet().stream()
                .map(entry -> new WeeklyAwardGrant(entry.getKey(), entry.getValue().stream().sorted().toList()))
                .toList();
    }
}
