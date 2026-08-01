package org.rimecraft.rimetools.module.chat.mixin;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.rimecraft.rimetools.module.chat.ChatFormatting;
import org.rimecraft.rimetools.module.chat.ChatModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Instant;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void rimeTools$governChat(PlayerChatMessage message, CallbackInfo callbackInfo) {
        ChatModule module = ChatModule.INSTANCE;
        if (module != null && module.handleChatMessage(player, message.signedContent(), now())) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "performUnsignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void rimeTools$governUnsignedCommand(String command, CallbackInfo callbackInfo) {
        ChatModule module = ChatModule.INSTANCE;
        if (module != null && module.handleCommand(player, command, now())) callbackInfo.cancel();
    }

    @Inject(method = "performSignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void rimeTools$governSignedCommand(ServerboundChatCommandSignedPacket packet,
                                                LastSeenMessages lastSeenMessages,
                                                CallbackInfo callbackInfo) {
        ChatModule module = ChatModule.INSTANCE;
        if (module != null && module.handleCommand(player, packet.command(), now())) callbackInfo.cancel();
    }

    @ModifyArg(
            method = "broadcastChatMessage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"),
            index = 2)
    private ChatType.Bound rimeTools$decorateChatType(ChatType.Bound original) {
        return ChatFormatting.decorate(original, player);
    }

    private static long now() { return Instant.now().getEpochSecond(); }
}
