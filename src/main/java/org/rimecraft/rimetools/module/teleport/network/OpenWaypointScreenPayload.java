package org.rimecraft.rimetools.module.teleport.network;

import org.rimecraft.rimetools.RimeTools;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.module.teleport.model.FakePlayerInfo;
import org.rimecraft.rimetools.module.teleport.model.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record OpenWaypointScreenPayload(
        int mode,
        UUID targetUuid,
        String targetName,
        List<Waypoint> personalWaypoints,
        List<Waypoint> globalWaypoints,
        List<FakePlayerInfo> fakePlayers,
        List<TeleportPlayerTarget> playerTargets,
        boolean canTpa,
        boolean canTpahere,
        boolean canLast,
        boolean canOtherPersonal,
        boolean canManageTpaAllowlist
) implements CustomPacketPayload {

    public static final Type<OpenWaypointScreenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "open_waypoint_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenWaypointScreenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenWaypointScreenPayload decode(FriendlyByteBuf buf) {
            int mode = buf.readVarInt();
            boolean hasTarget = buf.readBoolean();
            UUID targetUuid = hasTarget ? buf.readUUID() : null;
            String targetName = hasTarget ? buf.readUtf() : null;
            int personalCount = readCount(buf, 4096);
            List<Waypoint> personal = new ArrayList<>(personalCount);
            for (int i = 0; i < personalCount; i++) {
                personal.add(readWaypoint(buf));
            }
            int globalCount = readCount(buf, 4096);
            List<Waypoint> global = new ArrayList<>(globalCount);
            for (int i = 0; i < globalCount; i++) {
                global.add(readWaypoint(buf));
            }
            int fakePlayerCount = readCount(buf, 4096);
            List<FakePlayerInfo> fakePlayers = new ArrayList<>(fakePlayerCount);
            for (int i = 0; i < fakePlayerCount; i++) {
                fakePlayers.add(readFakePlayer(buf));
            }
            int playerCount = readCount(buf, 4096);
            List<TeleportPlayerTarget> players = new ArrayList<>(playerCount);
            for (int i = 0; i < playerCount; i++) {
                players.add(new TeleportPlayerTarget(
                        buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean()));
            }
            return new OpenWaypointScreenPayload(mode, targetUuid, targetName, personal, global, fakePlayers,
                    players, buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, OpenWaypointScreenPayload payload) {
            buf.writeVarInt(payload.mode);
            buf.writeBoolean(payload.targetUuid != null);
            if (payload.targetUuid != null) {
                buf.writeUUID(payload.targetUuid);
                buf.writeUtf(payload.targetName != null ? payload.targetName : "");
            }
            buf.writeVarInt(payload.personalWaypoints.size());
            for (Waypoint w : payload.personalWaypoints) {
                writeWaypoint(buf, w);
            }
            buf.writeVarInt(payload.globalWaypoints.size());
            for (Waypoint w : payload.globalWaypoints) {
                writeWaypoint(buf, w);
            }
            buf.writeVarInt(payload.fakePlayers.size());
            for (FakePlayerInfo fakePlayer : payload.fakePlayers) {
                writeFakePlayer(buf, fakePlayer);
            }
            buf.writeVarInt(payload.playerTargets.size());
            for (TeleportPlayerTarget player : payload.playerTargets) {
                buf.writeUUID(player.uuid());
                buf.writeUtf(player.name(), 64);
                buf.writeBoolean(player.online());
                buf.writeBoolean(player.tpaAllowed());
            }
            buf.writeBoolean(payload.canTpa);
            buf.writeBoolean(payload.canTpahere);
            buf.writeBoolean(payload.canLast);
            buf.writeBoolean(payload.canOtherPersonal);
            buf.writeBoolean(payload.canManageTpaAllowlist);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static int readCount(FriendlyByteBuf buf, int maximum) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid payload list size: " + count);
        }
        return count;
    }

    private static void writeWaypoint(FriendlyByteBuf buf, Waypoint w) {
        buf.writeUtf(w.getName());
        buf.writeUtf(w.getWorld());
        buf.writeDouble(w.getX());
        buf.writeDouble(w.getY());
        buf.writeDouble(w.getZ());
        buf.writeFloat(w.getYaw());
        buf.writeFloat(w.getPitch());
        String alias = w.getAlias();
        buf.writeBoolean(alias != null);
        if (alias != null) buf.writeUtf(alias);
        String desc = w.getDescription();
        buf.writeBoolean(desc != null);
        if (desc != null) buf.writeUtf(desc);
        UUID owner = w.getOwner();
        buf.writeBoolean(owner != null);
        if (owner != null) buf.writeUUID(owner);
        buf.writeLong(w.getCreatedAt());
        buf.writeLong(w.getUpdatedAt());
    }

    private static Waypoint readWaypoint(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        String world = buf.readUtf();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        String alias = buf.readBoolean() ? buf.readUtf() : null;
        String description = buf.readBoolean() ? buf.readUtf() : null;
        UUID owner = buf.readBoolean() ? buf.readUUID() : null;
        long createdAt = buf.readLong();
        long updatedAt = buf.readLong();
        return new Waypoint(name, world, x, y, z, yaw, pitch, alias, description, owner, createdAt, updatedAt);
    }

    private static void writeFakePlayer(FriendlyByteBuf buf, FakePlayerInfo fakePlayer) {
        buf.writeUtf(fakePlayer.name());
        buf.writeUtf(fakePlayer.world());
        buf.writeDouble(fakePlayer.x());
        buf.writeDouble(fakePlayer.y());
        buf.writeDouble(fakePlayer.z());
        buf.writeBoolean(fakePlayer.creatorName() != null);
        if (fakePlayer.creatorName() != null) buf.writeUtf(fakePlayer.creatorName());
        buf.writeBoolean(fakePlayer.ownedByViewer());
    }

    private static FakePlayerInfo readFakePlayer(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        String world = buf.readUtf();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        String creatorName = buf.readBoolean() ? buf.readUtf() : null;
        boolean ownedByViewer = buf.readBoolean();
        return new FakePlayerInfo(name, world, x, y, z, creatorName, ownedByViewer);
    }
}
