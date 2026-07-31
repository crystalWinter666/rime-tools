package org.rimecraft.rimetools.module.punishment.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.RimeTools;

public record MuteNoticePayload(long remainingSeconds) implements CustomPacketPayload {

    public static final Type<MuteNoticePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "mute_notice"));

    public static final StreamCodec<FriendlyByteBuf, MuteNoticePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MuteNoticePayload decode(FriendlyByteBuf buf) {
            return new MuteNoticePayload(buf.readLong());
        }

        @Override
        public void encode(FriendlyByteBuf buf, MuteNoticePayload payload) {
            buf.writeLong(payload.remainingSeconds);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
