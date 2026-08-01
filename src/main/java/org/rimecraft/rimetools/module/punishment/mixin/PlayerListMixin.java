package org.rimecraft.rimetools.module.punishment.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.rimecraft.rimetools.module.punishment.PunishmentModule;
import org.rimecraft.rimetools.module.punishment.PunishmentText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.time.Instant;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void rimeTools$checkBan(SocketAddress socketAddress, NameAndId nameAndId,
                                    CallbackInfoReturnable<Component> callbackInfo) {
        PunishmentModule module = PunishmentModule.INSTANCE;
        if (module == null) return;
        module.activeBan(nameAndId.id()).ifPresent(ban -> {
            long now = Instant.now().getEpochSecond();
            Component message = ban.expiresAt() == 0
                    ? PunishmentText.banned(ban)
                    : PunishmentText.tempBanned(ban, Math.max(0, ban.expiresAt() - now));
            callbackInfo.setReturnValue(message);
            callbackInfo.cancel();
        });
    }

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void rimeTools$warnMutedPlayer(Connection connection, ServerPlayer player,
                                           CommonListenerCookie cookie, CallbackInfo callbackInfo) {
        PunishmentModule module = PunishmentModule.INSTANCE;
        if (module == null) return;
        module.activeMute(player.getUUID()).ifPresent(mute -> {
            long remaining = Math.max(0, mute.expiresAt() - Instant.now().getEpochSecond());
            module.notifyMuted(player, mute, mute.expiresAt() == 0 ? -1 : remaining);
        });
    }
}
