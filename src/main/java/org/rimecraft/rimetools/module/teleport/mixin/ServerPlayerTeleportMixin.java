package org.rimecraft.rimetools.module.teleport.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import org.rimecraft.rimetools.module.teleport.TeleportModule;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Records a back point before every ServerPlayer teleport, so vanilla /tp and
 * teleports from other mods can be undone with /back.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    @Inject(
            method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
            at = @At("HEAD")
    )
    private void rimeTools$recordBackPoint(
            ServerLevel level, double x, double y, double z,
            Set<Relative> relative, float yRot, float xRot, boolean setPositionFlag,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        TeleportModule module = TeleportModule.INSTANCE;
        if (module != null) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            module.backs().set(player.getUUID(), TeleportPosition.from(player));
        }
    }
}
