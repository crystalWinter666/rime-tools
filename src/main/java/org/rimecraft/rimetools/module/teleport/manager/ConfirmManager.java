package org.rimecraft.rimetools.module.teleport.manager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.teleport.i18n.MessageService;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.rimecraft.rimetools.module.teleport.teleport.TeleportType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmManager {
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();

    public void register(PendingTeleport teleport) {
        pending.put(teleport.playerId(), teleport);
    }

    public PendingTeleport get(UUID uuid) {
        PendingTeleport value = pending.get(uuid);
        if (value != null && value.expiresAtMillis() <= System.currentTimeMillis()) {
            pending.remove(uuid, value);
            return null;
        }
        return value;
    }

    public PendingTeleport take(UUID uuid) {
        PendingTeleport value = pending.remove(uuid);
        return value != null && value.expiresAtMillis() > System.currentTimeMillis() ? value : null;
    }

    public void clear(UUID uuid) {
        pending.remove(uuid);
    }

    public void tick(MinecraftServer server, MessageService messages) {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAtMillis() > now) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) messages.send(player, "confirm.timeout");
            return true;
        });
    }

    public record PendingTeleport(UUID playerId, TeleportPosition destination, TeleportType type,
                                  long expiresAtMillis) { }
}
