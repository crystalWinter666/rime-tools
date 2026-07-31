package org.rimecraft.rimetools.module.teleport.network;

import org.rimecraft.rimetools.RimeTools;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TpaResultPayload(
        String otherName,   // the OTHER player's name
        boolean accepted    // true=accepted, false=denied
) implements CustomPacketPayload {

    public static final Type<TpaResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "tpa_result"));

    public static final StreamCodec<FriendlyByteBuf, TpaResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public TpaResultPayload decode(FriendlyByteBuf b) { return new TpaResultPayload(b.readUtf(), b.readBoolean()); }
        @Override public void encode(FriendlyByteBuf b, TpaResultPayload p) { b.writeUtf(p.otherName); b.writeBoolean(p.accepted); }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
