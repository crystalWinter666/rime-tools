package org.rimecraft.rimetools.module.teleport.manager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;

public final class CostManager {
    private final TeleportConfig.CostConfig config;
    private final Item configuredItem;

    public CostManager(TeleportConfig.CostConfig config) {
        this.config = config;
        configuredItem = BuiltInRegistries.ITEM.getOptional(Identifier.parse(config.item().itemId())).orElse(Items.DIAMOND);
    }

    public CostResult calculate(ServerPlayer player, TeleportPosition from, TeleportPosition to) {
        boolean crossworld = from != null && to != null
                && !TeleportConfig.normalizeWorld(from.world()).equalsIgnoreCase(TeleportConfig.normalizeWorld(to.world()));
        double distance = 0.0;
        if (from != null && to != null) {
            distance = crossworld && config.crossworld().mode() == TeleportConfig.CrossworldMode.FIXED_DISTANCE
                    ? config.crossworld().distance() : from.distanceTo(to);
        }
        int exp = config.exp().enabled() ? config.exp().base() + round(distance * config.exp().perBlock()) : 0;
        int items = config.item().enabled() ? config.item().base() + round(distance * config.item().perBlock()) : 0;
        if (crossworld && config.crossworld().mode() == TeleportConfig.CrossworldMode.EXTRA_COST) {
            if (config.exp().enabled()) exp += config.crossworld().extraCost();
            if (config.item().enabled()) items += config.crossworld().extraCost();
        }
        boolean affordable = (!config.exp().enabled() || player.totalExperience >= exp)
                && (!config.item().enabled() || countItems(player) >= items);
        return new CostResult(exp, items, affordable);
    }

    public void apply(ServerPlayer player, CostResult result) {
        if (config.exp().enabled() && result.expCost() > 0) player.giveExperiencePoints(-result.expCost());
        if (config.item().enabled() && result.itemCost() > 0) removeItems(player, result.itemCost());
    }

    private int countItems(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(stack)) count += stack.getCount();
        }
        return count;
    }

    private void removeItems(ServerPlayer player, int amount) {
        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matches(stack)) continue;
            int take = Math.min(stack.getCount(), remaining);
            stack.shrink(take);
            remaining -= take;
        }
    }

    private boolean matches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != configuredItem) return false;
        int expected = config.item().customModelData();
        if (expected < 0) return true;
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (data == null || data.floats().isEmpty()) return false;
        return Math.round(data.floats().getFirst()) == expected;
    }

    private int round(double value) {
        return switch (config.rounding()) {
            case FLOOR -> (int) Math.floor(value);
            case ROUND -> (int) Math.round(value);
            case CEIL -> (int) Math.ceil(value);
        };
    }

    public record CostResult(int expCost, int itemCost, boolean affordable) {
    }
}
