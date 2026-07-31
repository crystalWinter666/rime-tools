package org.rimecraft.rimetools.module.teleport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.RimeTools;

import java.util.UUID;

public record TpaAllowlistActionPayload(UUID targetUuid, boolean allowed) implements CustomPacketPayload {
    public static final Type<TpaAllowlistActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "tpa_allowlist_action"));

    public static final StreamCodec<FriendlyByteBuf, TpaAllowlistActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TpaAllowlistActionPayload decode(FriendlyByteBuf buf) {
            return new TpaAllowlistActionPayload(buf.readUUID(), buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, TpaAllowlistActionPayload payload) {
            buf.writeUUID(payload.targetUuid());
            buf.writeBoolean(payload.allowed());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
