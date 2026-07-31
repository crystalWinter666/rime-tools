package org.rimecraft.rimetools.module.title.permission;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface PermissionChecker {
    PermissionChecker NONE = (player, permission) -> false;

    boolean has(ServerPlayer player, String permission);

    default CompletableFuture<Boolean> update(String playerTarget, String titleId, boolean granted) {
        return CompletableFuture.completedFuture(false);
    }

    default CompletableFuture<List<KnownPlayer>> knownPlayers() {
        return CompletableFuture.completedFuture(List.of());
    }

    default CompletableFuture<Boolean> replaceManagedGrants(Set<String> titleIds,
                                                            Map<UUID, Set<String>> grants) {
        return CompletableFuture.completedFuture(false);
    }

    default CompletableFuture<Boolean> grantManaged(Map<UUID, Set<String>> grants) {
        return CompletableFuture.completedFuture(false);
    }

    default boolean available() {
        return this != NONE;
    }

    record KnownPlayer(UUID id, String name) {
    }
}
