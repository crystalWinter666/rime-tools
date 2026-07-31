package org.rimecraft.rimetools.module.teleport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.RimeTools;

import java.util.UUID;

public record WaypointActionPayload(
        int action,       // 0=TELEPORT, 1=DELETE, 2=EDIT_DESC, 3=CREATE, 4=REFRESH
        int scope,        // 0=PERSONAL, 1=GLOBAL
        int mode,         // 0=own, 1=admin
        UUID targetUuid,  // admin target (null for own)
        String waypointName,
        String alias,
        String description,
        boolean overwrite
) implements CustomPacketPayload {

    public static final Type<WaypointActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "waypoint_action"));

    public static final StreamCodec<FriendlyByteBuf, WaypointActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WaypointActionPayload decode(FriendlyByteBuf buf) {
            int action = buf.readVarInt();
            int scope = buf.readVarInt();
            int mode = buf.readVarInt();
            boolean hasTarget = buf.readBoolean();
            UUID targetUuid = hasTarget ? buf.readUUID() : null;
            String name = buf.readBoolean() ? buf.readUtf() : null;
            String alias = buf.readBoolean() ? buf.readUtf() : null;
            String desc = buf.readBoolean() ? buf.readUtf() : null;
            boolean overwrite = buf.readBoolean();
            return new WaypointActionPayload(action, scope, mode, targetUuid, name, alias, desc, overwrite);
        }

        @Override
        public void encode(FriendlyByteBuf buf, WaypointActionPayload payload) {
            buf.writeVarInt(payload.action);
            buf.writeVarInt(payload.scope);
            buf.writeVarInt(payload.mode);
            buf.writeBoolean(payload.targetUuid != null);
            if (payload.targetUuid != null) buf.writeUUID(payload.targetUuid);
            buf.writeBoolean(payload.waypointName != null);
            if (payload.waypointName != null) buf.writeUtf(payload.waypointName);
            buf.writeBoolean(payload.alias != null);
            if (payload.alias != null) buf.writeUtf(payload.alias);
            buf.writeBoolean(payload.description != null);
            if (payload.description != null) buf.writeUtf(payload.description);
            buf.writeBoolean(payload.overwrite);
        }
    };
    // Action constants
    public static final int ACTION_TELEPORT = 0;
    public static final int ACTION_DELETE = 1;
    public static final int ACTION_EDIT_DESC = 2;
    public static final int ACTION_CREATE = 3;
    public static final int ACTION_REFRESH = 4;
    public static final int ACTION_TELEPORT_FAKE = 5;
    // Scope constants
    public static final int SCOPE_PERSONAL = 0;
    public static final int SCOPE_GLOBAL = 1;
    public static final int SCOPE_FAKE_PLAYER = 2;
    // Mode constants
    public static final int MODE_OWN = 0;
    public static final int MODE_ADMIN = 1;
    public static final int MODE_OTHER_READ_ONLY = 2;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
