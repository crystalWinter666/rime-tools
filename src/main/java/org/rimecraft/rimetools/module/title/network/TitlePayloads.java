package org.rimecraft.rimetools.module.title.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.RimeTools;

import java.util.List;

public final class TitlePayloads {
    public static final int PROTOCOL_VERSION = 2;

    public record RequestTitles(int protocolVersion) implements CustomPacketPayload {
        public static final Type<RequestTitles> TYPE = new Type<>(id("request_titles"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RequestTitles> CODEC =
                ByteBufCodecs.VAR_INT.map(RequestTitles::new, RequestTitles::protocolVersion).mapStream(value -> value);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SelectTitle(String titleId) implements CustomPacketPayload {
        public static final Type<SelectTitle> TYPE = new Type<>(id("select_title"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SelectTitle> CODEC =
                ByteBufCodecs.stringUtf8(32).map(SelectTitle::new, SelectTitle::titleId).mapStream(value -> value);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TitleMeta(String id, String displayName, String color, int weight) {
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TitleMeta> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), TitleMeta::id,
                ByteBufCodecs.stringUtf8(64), TitleMeta::displayName,
                ByteBufCodecs.stringUtf8(7), TitleMeta::color,
                ByteBufCodecs.VAR_INT, TitleMeta::weight,
                TitleMeta::new
        );
    }

    public record TitleEntry(TitleMeta meta, boolean enabled, boolean unlocked, boolean selected) {
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TitleEntry> CODEC = StreamCodec.composite(
                TitleMeta.CODEC, TitleEntry::meta,
                ByteBufCodecs.BOOL, TitleEntry::enabled,
                ByteBufCodecs.BOOL, TitleEntry::unlocked,
                ByteBufCodecs.BOOL, TitleEntry::selected,
                TitleEntry::new
        );

        public String id() {
            return meta.id();
        }

        public String displayName() {
            return meta.displayName();
        }

        public String color() {
            return meta.color();
        }

        public int weight() {
            return meta.weight();
        }
    }

    public record Capabilities(boolean permissionsAvailable, boolean canManageTitles, boolean canAssignTitles) {
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Capabilities> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Capabilities::permissionsAvailable,
                ByteBufCodecs.BOOL, Capabilities::canManageTitles,
                ByteBufCodecs.BOOL, Capabilities::canAssignTitles,
                Capabilities::new
        );
    }

    public record PlayerTarget(String name, String uuid, boolean online) {
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, PlayerTarget> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), PlayerTarget::name,
                ByteBufCodecs.stringUtf8(36), PlayerTarget::uuid,
                ByteBufCodecs.BOOL, PlayerTarget::online,
                PlayerTarget::new
        );
    }

    public record TitlesResponse(int protocolVersion, List<TitleEntry> titles, String fallbackTitle,
                                 String fallbackColor, Capabilities capabilities,
                                 List<PlayerTarget> playerTargets) implements CustomPacketPayload {
        public static final Type<TitlesResponse> TYPE = new Type<>(id("titles_response"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TitlesResponse> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TitlesResponse::protocolVersion,
                TitleEntry.CODEC.apply(ByteBufCodecs.list(128)), TitlesResponse::titles,
                ByteBufCodecs.stringUtf8(64), TitlesResponse::fallbackTitle,
                ByteBufCodecs.stringUtf8(7), TitlesResponse::fallbackColor,
                Capabilities.CODEC, TitlesResponse::capabilities,
                PlayerTarget.CODEC.apply(ByteBufCodecs.list(8192)), TitlesResponse::playerTargets,
                TitlesResponse::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpsertTitle(String id, String displayName, String color, int weight, boolean enabled)
            implements CustomPacketPayload {
        public static final Type<UpsertTitle> TYPE = new Type<>(TitlePayloads.id("upsert_title"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, UpsertTitle> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), UpsertTitle::id,
                ByteBufCodecs.stringUtf8(64), UpsertTitle::displayName,
                ByteBufCodecs.stringUtf8(7), UpsertTitle::color,
                ByteBufCodecs.VAR_INT, UpsertTitle::weight,
                ByteBufCodecs.BOOL, UpsertTitle::enabled,
                UpsertTitle::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DeleteTitle(String titleId) implements CustomPacketPayload {
        public static final Type<DeleteTitle> TYPE = new Type<>(id("delete_title"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, DeleteTitle> CODEC =
                ByteBufCodecs.stringUtf8(32).map(DeleteTitle::new, DeleteTitle::titleId).mapStream(value -> value);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AssignTitle(String playerTarget, String titleId, boolean granted) implements CustomPacketPayload {
        public static final Type<AssignTitle> TYPE = new Type<>(id("assign_title"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, AssignTitle> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), AssignTitle::playerTarget,
                ByteBufCodecs.stringUtf8(32), AssignTitle::titleId,
                ByteBufCodecs.BOOL, AssignTitle::granted,
                AssignTitle::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OperationResult(boolean success, String messageKey) implements CustomPacketPayload {
        public static final Type<OperationResult> TYPE = new Type<>(id("operation_result"));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, OperationResult> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OperationResult::success,
                ByteBufCodecs.stringUtf8(64), OperationResult::messageKey,
                OperationResult::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private TitlePayloads() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, path);
    }
}
