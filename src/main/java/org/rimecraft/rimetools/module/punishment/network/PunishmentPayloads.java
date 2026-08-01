package org.rimecraft.rimetools.module.punishment.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.RimeTools;

import java.util.List;

/** Bounded versioned payloads for the punishment administration screen. */
public final class PunishmentPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PAGE_SIZE = 50;
    private PunishmentPayloads() { }
    private static Identifier id(String value) { return RimeTools.id(value); }

    public record Request(int version, String query, int page) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("punishment/request"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::version,
                ByteBufCodecs.stringUtf8(64), Request::query,
                ByteBufCodecs.VAR_INT, Request::page,
                Request::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PlayerEntry(String uuid, String name, boolean online, int warnings, boolean banned, boolean muted) {
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, PlayerEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), PlayerEntry::uuid,
                ByteBufCodecs.stringUtf8(36), PlayerEntry::name,
                ByteBufCodecs.BOOL, PlayerEntry::online,
                ByteBufCodecs.VAR_INT, PlayerEntry::warnings,
                ByteBufCodecs.BOOL, PlayerEntry::banned,
                ByteBufCodecs.BOOL, PlayerEntry::muted,
                PlayerEntry::new);
    }

    public record RecordEntry(String id, String playerUuid, String playerName, String type, String status,
                              long issuedAt, long expiresAt, String reason, String executor) {
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RecordEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), RecordEntry::id,
                ByteBufCodecs.stringUtf8(36), RecordEntry::playerUuid,
                ByteBufCodecs.stringUtf8(36), RecordEntry::playerName,
                ByteBufCodecs.stringUtf8(16), RecordEntry::type,
                ByteBufCodecs.stringUtf8(16), RecordEntry::status,
                ByteBufCodecs.VAR_LONG, RecordEntry::issuedAt,
                ByteBufCodecs.VAR_LONG, RecordEntry::expiresAt,
                ByteBufCodecs.stringUtf8(256), RecordEntry::reason,
                ByteBufCodecs.stringUtf8(64), RecordEntry::executor,
                RecordEntry::new);
    }

    public record Response(int version, List<PlayerEntry> players, List<RecordEntry> records,
                           int page, int totalPages, boolean canApply, boolean canRevoke) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(id("punishment/response"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Response::version,
                PlayerEntry.CODEC.apply(ByteBufCodecs.list(512)), Response::players,
                RecordEntry.CODEC.apply(ByteBufCodecs.list(MAX_PAGE_SIZE)), Response::records,
                ByteBufCodecs.VAR_INT, Response::page,
                ByteBufCodecs.VAR_INT, Response::totalPages,
                ByteBufCodecs.BOOL, Response::canApply,
                ByteBufCodecs.BOOL, Response::canRevoke,
                Response::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Action(String playerUuid, String playerName, String action, long duration,
                         String reason, String recordId) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("punishment/action"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), Action::playerUuid,
                ByteBufCodecs.stringUtf8(36), Action::playerName,
                ByteBufCodecs.stringUtf8(16), Action::action,
                ByteBufCodecs.VAR_LONG, Action::duration,
                ByteBufCodecs.stringUtf8(256), Action::reason,
                ByteBufCodecs.stringUtf8(36), Action::recordId,
                Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Result(boolean success, String message) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(id("punishment/result"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Result::success,
                ByteBufCodecs.stringUtf8(256), Result::message,
                Result::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
