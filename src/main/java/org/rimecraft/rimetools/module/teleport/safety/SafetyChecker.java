package org.rimecraft.rimetools.module.teleport.safety;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;

import java.util.Set;

public final class SafetyChecker {
    private static final Set<Block> DANGEROUS = Set.of(
            Blocks.LAVA, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
            Blocks.CACTUS, Blocks.MAGMA_BLOCK, Blocks.WITHER_ROSE
    );

    private final TeleportConfig.SafetyConfig config;

    public SafetyChecker(TeleportConfig.SafetyConfig config) {
        this.config = config;
    }

    public Result check(ServerLevel level, TeleportPosition position) {
        if (level == null || position == null) return new Result(false, "invalid_world");
        int x = floor(position.x());
        int y = floor(position.y());
        int z = floor(position.z());
        int minY = Math.max(config.minY(), level.getMinY());
        if (position.y() <= minY) return new Result(false, "void");

        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = feetPos.above();
        BlockPos belowPos = feetPos.below();
        BlockState feet = level.getBlockState(feetPos);
        BlockState head = level.getBlockState(headPos);
        BlockState below = level.getBlockState(belowPos);

        if (!feet.getCollisionShape(level, feetPos).isEmpty() || !head.getCollisionShape(level, headPos).isEmpty()) {
            return new Result(false, "blocked");
        }
        if (below.getCollisionShape(level, belowPos).isEmpty()) {
            return new Result(false, "no_floor");
        }
        if (DANGEROUS.contains(feet.getBlock()) || DANGEROUS.contains(below.getBlock())) {
            return new Result(false, "danger");
        }
        if (config.disallowWater() && (!level.getFluidState(feetPos).isEmpty() || !level.getFluidState(headPos).isEmpty())) {
            return new Result(false, "water");
        }
        return new Result(true, "safe");
    }

    public TeleportPosition findNearby(ServerLevel level, TeleportPosition origin) {
        int originX = floor(origin.x());
        int originY = floor(origin.y());
        int originZ = floor(origin.z());
        for (int radius = 0; radius <= config.searchRadius(); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = -config.searchVertical(); dy <= config.searchVertical(); dy++) {
                        int y = originY + dy;
                        if (y <= level.getMinY() || y >= level.getMaxY()) continue;
                        TeleportPosition candidate = new TeleportPosition(origin.world(), originX + dx + 0.5, y,
                                originZ + dz + 0.5, origin.yaw(), origin.pitch());
                        if (check(level, candidate).safe()) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }

    public record Result(boolean safe, String reason) { }
}
