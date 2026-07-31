package org.rimecraft.rimetools.carpet.mixin;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
    }
}
