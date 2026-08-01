package org.rimecraft.rimetools.carpet.mixin;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.rimecraft.rimetools.carpet.fakeplayer.FakePlayerInventoryStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps fake-player inventories intact across death: holds the inventory during
 * the death sequence and restores it afterwards.
 */
@Mixin(EntityPlayerMPFake.class)
public abstract class EntityPlayerMPFakeMixin {
    @Unique
    private List<ItemStack> rimetools$inventoryBeforeDeath;

    @Inject(method = "die", at = @At("HEAD"))
    private void rimetools$holdInventoryDuringDeath(DamageSource source, CallbackInfo callbackInfo) {
        EntityPlayerMPFake player = (EntityPlayerMPFake) (Object) this;
        Inventory inventory = player.getInventory();
        this.rimetools$inventoryBeforeDeath = new ArrayList<>(inventory.getContainerSize());

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.removeItemNoUpdate(slot);
            this.rimetools$inventoryBeforeDeath.add(stack);
        }
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void rimetools$restoreInventoryAfterDeath(DamageSource source, CallbackInfo callbackInfo) {
        if (this.rimetools$inventoryBeforeDeath == null) {
            return;
        }

        EntityPlayerMPFake player = (EntityPlayerMPFake) (Object) this;
        Inventory inventory = player.getInventory();
        int slotCount = Math.min(inventory.getContainerSize(), this.rimetools$inventoryBeforeDeath.size());
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = this.rimetools$inventoryBeforeDeath.get(slot);
            inventory.setItem(slot, stack);
        }
        this.rimetools$inventoryBeforeDeath = null;

        // Snapshot the restored inventory so the next spawn of this fake player gets it back.
        List<ItemStack> slots = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            slots.add(inventory.getItem(slot));
        }
        FakePlayerInventoryStore.save(player.getGameProfile().name(), slots);
    }

    @Inject(method = "loadPlayerData", at = @At("RETURN"))
    private static void rimetools$restoreStoredInventory(EntityPlayerMPFake fakePlayer, CallbackInfo callbackInfo) {
        if (fakePlayer.isAShadow) {
            return; // shadow copies a real player's data; never overwrite it with a fake snapshot
        }
        Inventory inventory = fakePlayer.getInventory();
        List<ItemStack> target = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            target.add(inventory.getItem(slot));
        }
        if (FakePlayerInventoryStore.restore(fakePlayer.getGameProfile().name(), target)) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                inventory.setItem(slot, target.get(slot));
            }
        }
    }
}
