package org.rimecraft.rimetools.module.teleport.teleport;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.i18n.MessageService;
import org.rimecraft.rimetools.module.teleport.manager.CooldownManager;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.rimecraft.rimetools.module.teleport.safety.SafetyChecker;
import org.rimecraft.rimetools.module.teleport.util.Permissions;
import org.rimecraft.rimetools.module.teleport.util.TimeUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomTeleportService {
    private static final int COORDINATE_ATTEMPTS = 32;

    private final MinecraftServer server;
    private final TeleportConfig config;
    private final MessageService messages;
    private final CooldownManager cooldowns;
    private final SafetyChecker safety;
    private final TeleportService teleports;
    private final Set<UUID> pending = new HashSet<>();

    public RandomTeleportService(MinecraftServer server, TeleportConfig config, MessageService messages,
                                 CooldownManager cooldowns, SafetyChecker safety, TeleportService teleports) {
        this.server = server;
        this.config = config;
        this.messages = messages;
        this.cooldowns = cooldowns;
        this.safety = safety;
        this.teleports = teleports;
    }

    public boolean start(ServerPlayer player) {
        if (!config.randomTeleport.enabled()) {
            messages.send(player, "rtp.disabled");
            return false;
        }
        if (!Permissions.has(player, "rtp", true)) {
            messages.send(player, "no_permission");
            return false;
        }
        if (!config.isWorldAllowed(TeleportPosition.worldName(player.level()))) {
            messages.send(player, "world.not_allowed");
            return false;
        }
        if (!Permissions.has(player, "cooldown.bypass", false)) {
            long remaining = cooldowns.remaining(player.getUUID(), CooldownManager.Type.RANDOM);
            if (remaining > 0) {
                messages.send(player, "cooldown.wait", MessageService.vars("time", TimeUtil.formatSeconds(remaining)));
                return false;
            }
        }
        Admission admission = admit(player.getUUID());
        if (admission == Admission.ALREADY_PENDING) {
            messages.send(player, "rtp.pending");
            return false;
        }
        if (admission == Admission.SERVER_BUSY) {
            messages.send(player, "rtp.busy");
            return false;
        }

        ServerLevel level = player.level();
        Search search = new Search(player.getUUID(), level, player.getX(), player.getZ(),
                TeleportPosition.worldName(level), player.getYRot(), player.getXRot());
        messages.send(player, "rtp.searching");
        searchNext(player, search);
        return true;
    }

    private void searchNext(ServerPlayer player, Search search) {
        if (search.loadedAttempts >= config.randomTeleport.maxAttempts()) {
            fail(player);
            return;
        }
        Candidate candidate = nextCandidate(search);
        if (candidate == null) {
            fail(player);
            return;
        }

        search.loadedAttempts++;
        search.level.getChunkSource()
                .getChunkFuture(candidate.chunkX, candidate.chunkZ, ChunkStatus.FULL, true)
                .whenComplete((result, throwable) -> server.execute(() -> inspect(player, search, candidate, result, throwable)));
    }

    private void inspect(ServerPlayer originalPlayer, Search search, Candidate candidate,
                         ChunkResult<ChunkAccess> result, Throwable throwable) {
        ServerPlayer player = server.getPlayerList().getPlayer(search.playerId);
        if (player == null || player != originalPlayer || player.hasDisconnected() || player.level() != search.level) {
            complete(search.playerId);
            return;
        }
        ChunkAccess chunk = throwable == null && result != null ? result.orElse(null) : null;
        if (chunk == null) {
            searchNext(player, search);
            return;
        }

        int localX = Math.floorMod(candidate.blockX, 16);
        int localZ = Math.floorMod(candidate.blockZ, 16);
        int height = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) + 1;
        TeleportPosition destination = findSafeDestination(search, candidate, height);
        if (destination == null) {
            searchNext(player, search);
            return;
        }

        complete(search.playerId);
        TeleportService.Result teleportResult = teleports.teleport(player, destination, TeleportType.RANDOM, false);
        if (teleportResult == TeleportService.Result.SUCCESS) {
            messages.send(player, "rtp.success", MessageService.vars(
                    "x", String.format(java.util.Locale.US, "%.1f", destination.x()),
                    "y", String.format(java.util.Locale.US, "%.1f", destination.y()),
                    "z", String.format(java.util.Locale.US, "%.1f", destination.z())));
        }
    }

    private TeleportPosition findSafeDestination(Search search, Candidate candidate, int height) {
        int startY = height;
        int endY = height;
        if (search.level.dimensionType().hasCeiling()) {
            startY = Math.min(height, search.level.getMinY() + search.level.getLogicalHeight() - 2);
            endY = search.level.getMinY() + 1;
        }
        for (int y = startY; y >= endY; y--) {
            TeleportPosition position = new TeleportPosition(search.world, candidate.blockX + 0.5, y,
                    candidate.blockZ + 0.5, search.yaw, search.pitch);
            if (safety.check(search.level, position).safe()) return position;
        }
        return null;
    }

    private Candidate nextCandidate(Search search) {
        double minSquared = (double) config.randomTeleport.minRadius() * config.randomTeleport.minRadius();
        double maxSquared = (double) config.randomTeleport.maxRadius() * config.randomTeleport.maxRadius();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < COORDINATE_ATTEMPTS; attempt++) {
            double radius = Math.sqrt(minSquared + random.nextDouble() * (maxSquared - minSquared));
            double angle = random.nextDouble(Math.PI * 2.0);
            int blockX = (int) Math.floor(search.centerX + Math.cos(angle) * radius);
            int blockZ = (int) Math.floor(search.centerZ + Math.sin(angle) * radius);
            if (!search.level.getWorldBorder().isWithinBounds(blockX + 0.5, blockZ + 0.5)) continue;
            int chunkX = Math.floorDiv(blockX, 16);
            int chunkZ = Math.floorDiv(blockZ, 16);
            if (!search.triedChunks.add(ChunkPos.pack(chunkX, chunkZ))) continue;
            return new Candidate(blockX, blockZ, chunkX, chunkZ);
        }
        return null;
    }

    private void fail(ServerPlayer player) {
        complete(player.getUUID());
        if (!player.hasDisconnected()) messages.send(player, "rtp.failed");
    }

    private synchronized Admission admit(UUID playerId) {
        if (pending.contains(playerId)) return Admission.ALREADY_PENDING;
        if (pending.size() >= config.randomTeleport.maxConcurrentSearches()) return Admission.SERVER_BUSY;
        pending.add(playerId);
        return Admission.ACCEPTED;
    }

    private synchronized void complete(UUID playerId) {
        pending.remove(playerId);
    }

    private enum Admission {
        ACCEPTED,
        ALREADY_PENDING,
        SERVER_BUSY
    }

    private static final class Search {
        private final UUID playerId;
        private final ServerLevel level;
        private final double centerX;
        private final double centerZ;
        private final String world;
        private final float yaw;
        private final float pitch;
        private final Set<Long> triedChunks = new HashSet<>();
        private int loadedAttempts;

        private Search(UUID playerId, ServerLevel level, double centerX, double centerZ,
                       String world, float yaw, float pitch) {
            this.playerId = playerId;
            this.level = level;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.world = world;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private record Candidate(int blockX, int blockZ, int chunkX, int chunkZ) { }
}
