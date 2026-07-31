package org.rimecraft.rimetools.module.teleport.manager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.i18n.MessageService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaManager {
    private final Map<String, TpaRequest> requests = new ConcurrentHashMap<>();

    private String key(UUID sender, UUID target) {
        return sender + ":" + target;
    }

    public synchronized TpaRequest get(UUID sender, UUID target) {
        return requests.get(key(sender, target));
    }

    public synchronized TpaRequest latest(UUID target) {
        return requests.values().stream()
                .filter(request -> request.targetId().equals(target))
                .max(Comparator.comparingLong(TpaRequest::createdAt))
                .orElse(null);
    }

    public synchronized List<TpaRequest> forTarget(UUID target) {
        return requests.values().stream()
                .filter(request -> request.targetId().equals(target))
                .sorted(Comparator.comparingLong(TpaRequest::createdAt).reversed())
                .toList();
    }

    public synchronized boolean add(TpaRequest request, TeleportConfig.DuplicatePolicy policy) {
        String key = key(request.senderId(), request.targetId());
        if (requests.containsKey(key) && policy == TeleportConfig.DuplicatePolicy.REJECT) return false;
        requests.put(key, request);
        return true;
    }

    public synchronized void remove(TpaRequest request) {
        requests.remove(key(request.senderId(), request.targetId()));
    }

    public synchronized List<TpaRequest> removeAllFrom(UUID sender) {
        List<TpaRequest> removed = new ArrayList<>();
        requests.entrySet().removeIf(entry -> {
            if (!entry.getValue().senderId().equals(sender)) return false;
            removed.add(entry.getValue());
            return true;
        });
        return removed;
    }

    public synchronized void tick(MinecraftServer server, MessageService messages) {
        long now = Instant.now().getEpochSecond();
        List<TpaRequest> expired = new ArrayList<>();
        requests.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt() > now) return false;
            expired.add(entry.getValue());
            return true;
        });
        for (TpaRequest request : expired) {
            ServerPlayer sender = server.getPlayerList().getPlayer(request.senderId());
            ServerPlayer target = server.getPlayerList().getPlayer(request.targetId());
            if (sender != null) {
                messages.send(sender, "tpa.timeout.sender", MessageService.vars("player", target == null ? "?" : target.getName().getString()));
            }
            if (target != null) {
                messages.send(target, "tpa.timeout.target", MessageService.vars("player", sender == null ? "?" : sender.getName().getString()));
            }
        }
    }

    public enum Type { TO_TARGET, HERE }
    public record TpaRequest(UUID senderId, UUID targetId, Type type, long createdAt, long expiresAt) { }
}
