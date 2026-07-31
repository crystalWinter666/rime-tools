package org.rimecraft.rimetools.module.title.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.util.Tristate;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsTitleAccess implements PermissionChecker {
    private final LuckPerms luckPerms;

    private LuckPermsTitleAccess(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public static LuckPermsTitleAccess create() {
        LuckPerms luckPerms = lookup();
        return luckPerms == null ? null : new LuckPermsTitleAccess(luckPerms);
    }

    public static LuckPerms lookup() {
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    public static boolean has(User user, String permission) {
        return user != null && user.getCachedData().getPermissionData().checkPermission(permission) == Tristate.TRUE;
    }

    public static CompletableFuture<Boolean> update(User user, String titleId, boolean value, LuckPerms luckPerms) {
        if (user == null || luckPerms == null) {
            return CompletableFuture.completedFuture(false);
        }
        PermissionNode node = luckPerms.getNodeBuilderRegistry().forPermission()
                .permission(TitlePermissions.title(titleId))
                .value(true)
                .build();
        DataMutateResult result = value ? user.data().add(node) : user.data().remove(node);
        if (!result.wasSuccessful() && result != DataMutateResult.FAIL_ALREADY_HAS && result != DataMutateResult.FAIL_LACKS) {
            return CompletableFuture.completedFuture(false);
        }
        user.getCachedData().invalidate();
        return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> true);
    }

    public static User loadedUser(LuckPerms luckPerms, UUID player) {
        return luckPerms == null ? null : luckPerms.getUserManager().getUser(player);
    }

    public LuckPerms luckPerms() {
        return luckPerms;
    }

    @Override
    public boolean has(net.minecraft.server.level.ServerPlayer player, String permission) {
        return has(loadedUser(luckPerms, player.getUUID()), permission);
    }

    @Override
    public CompletableFuture<Boolean> update(String playerTarget, String titleId, boolean granted) {
        CompletableFuture<UUID> lookup;
        try {
            lookup = CompletableFuture.completedFuture(UUID.fromString(playerTarget));
        } catch (IllegalArgumentException ignored) {
            lookup = luckPerms.getUserManager().lookupUniqueId(playerTarget);
        }
        return lookup
                .thenCompose(playerId -> playerId == null
                        ? CompletableFuture.completedFuture(null)
                        : luckPerms.getUserManager().loadUser(playerId))
                .thenCompose(user -> update(user, titleId, granted, luckPerms))
                .exceptionally(ignored -> false);
    }

    @Override
    public CompletableFuture<List<KnownPlayer>> knownPlayers() {
        List<KnownPlayer> loaded = luckPerms.getUserManager().getLoadedUsers().stream()
                .map(user -> new KnownPlayer(user.getUniqueId(), user.getUsername()))
                .toList();
        return CompletableFuture.completedFuture(loaded);
    }

    @Override
    public CompletableFuture<Boolean> replaceManagedGrants(Set<String> titleIds,
                                                            Map<UUID, Set<String>> grants) {
        List<CompletableFuture<Boolean>> updates = grants.entrySet().stream()
                .map(entry -> luckPerms.getUserManager().loadUser(entry.getKey())
                        .thenCompose(user -> replaceManagedGrants(user, titleIds, entry.getValue()))
                        .exceptionally(ignored -> false))
                .toList();
        return CompletableFuture.allOf(updates.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> updates.stream().allMatch(CompletableFuture::join));
    }

    @Override
    public CompletableFuture<Boolean> grantManaged(Map<UUID, Set<String>> grants) {
        List<CompletableFuture<Boolean>> updates = grants.entrySet().stream()
                .map(entry -> luckPerms.getUserManager().loadUser(entry.getKey())
                        .thenCompose(user -> grantManaged(user, entry.getValue()))
                        .exceptionally(ignored -> false))
                .toList();
        return CompletableFuture.allOf(updates.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> updates.stream().allMatch(CompletableFuture::join));
    }

    private CompletableFuture<Boolean> replaceManagedGrants(User user, Set<String> titleIds,
                                                             Set<String> desiredTitleIds) {
        if (user == null) return CompletableFuture.completedFuture(false);
        titleIds.forEach(titleId -> user.data().remove(permissionNode(titleId)));
        desiredTitleIds.forEach(titleId -> user.data().add(permissionNode(titleId)));
        user.getCachedData().invalidate();
        return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> true);
    }

    private CompletableFuture<Boolean> grantManaged(User user, Set<String> titleIds) {
        if (user == null) return CompletableFuture.completedFuture(false);
        titleIds.forEach(titleId -> user.data().add(permissionNode(titleId)));
        user.getCachedData().invalidate();
        return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> true);
    }

    private PermissionNode permissionNode(String titleId) {
        return luckPerms.getNodeBuilderRegistry().forPermission()
                .permission(TitlePermissions.title(titleId))
                .value(true)
                .build();
    }
}
