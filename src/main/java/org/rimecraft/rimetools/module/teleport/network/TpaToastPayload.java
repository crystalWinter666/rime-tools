package org.rimecraft.rimetools.module.teleport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.RimeTools;

public record TpaToastPayload(
        String senderName,
        int requestType, // 0=TO_TARGET, 1=HERE
        int timeoutSeconds,
        boolean sent        // true=this is for the requester (sent toast), false=for the target (incoming)
) implements CustomPacketPayload {

    public static final Type<TpaToastPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "tpa_toast"));

    public static final StreamCodec<FriendlyByteBuf, TpaToastPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TpaToastPayload decode(FriendlyByteBuf buf) {
            return new TpaToastPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, TpaToastPayload payload) {
            buf.writeUtf(payload.senderName);
            buf.writeVarInt(payload.requestType);
            buf.writeVarInt(payload.timeoutSeconds);
            buf.writeBoolean(payload.sent);
        }
    };
    public static final int TYPE_TO_TARGET = 0;
    public static final int TYPE_HERE = 1;
    public static final int TYPE_AUTO = 2;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Avoid conflict with CustomPacketPayload.type()
    public int requestType() {
        return requestType;
    }

    public String senderName() {
        return senderName;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }
}
