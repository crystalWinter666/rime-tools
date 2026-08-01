package org.rimecraft.rimetools.carpet.fakeplayer;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a fake player's inventory snapshot after it dies so the next spawn of the
 * same-named fake player gets its items back. In-memory only: snapshots are lost on
 * server restart. Restoring consumes the entry.
 */
public final class FakePlayerInventoryStore {
    private static final Map<String, List<ItemStack>> INVENTORIES = new ConcurrentHashMap<>();

    private FakePlayerInventoryStore() {
    }

    /**
     * Snapshots the given slots (one stack per slot; empty slots must be
     * {@link ItemStack#EMPTY}) under the fake player's name. Each stack is copied, so
     * later mutations of the source list do not affect the stored snapshot.
     */
    public static void save(String name, List<ItemStack> slots) {
        List<ItemStack> copy = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        INVENTORIES.put(normalize(name), copy);
    }

    /**
     * Writes a stored snapshot into {@code target} (up to the smaller of the two sizes),
     * consumes the entry, and returns {@code true}. Returns {@code false} without touching
     * {@code target} when no snapshot exists for the name.
     */
    public static boolean restore(String name, List<ItemStack> target) {
        List<ItemStack> stored = INVENTORIES.remove(normalize(name));
        if (stored == null) {
            return false;
        }
        int slots = Math.min(target.size(), stored.size());
        for (int slot = 0; slot < slots; slot++) {
            target.set(slot, stored.get(slot));
        }
        return true;
    }

    private static String normalize(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
