package org.rimecraft.rimetools.carpet.fakeplayer;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePlayerInventoryStoreTest {
    private static final int SLOT_COUNT = 41;

    @BeforeAll
    static void bootstrapMinecraft() {
        // ItemStack construction needs the vanilla registries, which are not initialized in a
        // bare JUnit JVM. Bootstrap them, then bind every item holder's data components to
        // EMPTY: 26.2 binds real components during server resource reload, which a unit test
        // does not run, and unbound holders make ItemStack construction throw.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (Item item : BuiltInRegistries.ITEM) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    private static ItemStack testStack(int count) {
        return new ItemStack(Items.DIAMOND, count);
    }

    private static List<ItemStack> slots(int stacks, int firstSlot) {
        List<ItemStack> slots = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            slots.add(ItemStack.EMPTY);
        }
        for (int i = 0; i < stacks; i++) {
            slots.set(firstSlot + i, testStack(1));
        }
        return slots;
    }

    private static List<ItemStack> emptySlots() {
        return slots(0, 0);
    }

    @Test
    void restoreReturnsStoredContentsAndConsumesEntry() {
        List<ItemStack> saved = slots(5, 2);
        FakePlayerInventoryStore.save("Steve", saved);

        List<ItemStack> target = emptySlots();
        assertTrue(FakePlayerInventoryStore.restore("Steve", target));
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            assertTrue(ItemStack.matches(saved.get(slot), target.get(slot)), "slot " + slot);
        }

        List<ItemStack> second = emptySlots();
        assertFalse(FakePlayerInventoryStore.restore("Steve", second));
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            assertTrue(ItemStack.matches(ItemStack.EMPTY, second.get(slot)), "slot " + slot);
        }
    }

    @Test
    void playerNameLookupIsCaseInsensitive() {
        FakePlayerInventoryStore.save("Test", slots(1, 0));

        List<ItemStack> target = emptySlots();
        assertTrue(FakePlayerInventoryStore.restore("test", target));
        assertTrue(ItemStack.matches(testStack(1), target.get(0)));
        assertFalse(FakePlayerInventoryStore.restore("TEST", emptySlots()));
    }

    @Test
    void restoreWithoutSnapshotLeavesTargetUntouched() {
        List<ItemStack> target = slots(3, 5);
        List<ItemStack> before = new ArrayList<>(target);

        assertFalse(FakePlayerInventoryStore.restore("Nobody", target));
        assertTrue(ItemStack.listMatches(before, target));
    }

    @Test
    void mutatingSourceListAfterSaveDoesNotAffectSnapshot() {
        List<ItemStack> saved = slots(4, 1);
        FakePlayerInventoryStore.save("Alex", saved);

        saved.set(1, ItemStack.EMPTY);
        saved.clear();

        List<ItemStack> target = emptySlots();
        assertTrue(FakePlayerInventoryStore.restore("Alex", target));
        assertTrue(ItemStack.matches(testStack(1), target.get(1)));
        assertTrue(ItemStack.matches(ItemStack.EMPTY, target.get(0)));
    }

    @Test
    void mutatingSourceStackAfterSaveDoesNotAffectSnapshot() {
        List<ItemStack> saved = slots(1, 0);
        saved.set(0, testStack(64));
        FakePlayerInventoryStore.save("Notch", saved);

        // save() copies each stack; mutating the original afterwards must not leak through.
        saved.get(0).shrink(32);
        saved.get(0).setCount(1);

        List<ItemStack> target = emptySlots();
        assertTrue(FakePlayerInventoryStore.restore("Notch", target));
        assertEquals(64, target.get(0).getCount());
    }
}
