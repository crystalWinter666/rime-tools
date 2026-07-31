package org.rimecraft.rimetools.module.teleport.model;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;

public record TeleportPosition(String world, double x, double y, double z, float yaw, float pitch) {
    public static TeleportPosition from(ServerPlayer player) {
        return new TeleportPosition(worldName(player.level()), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    public static String worldName(ServerLevel level) {
        return TeleportConfig.normalizeWorld(level.dimension().identifier().toString());
    }

    public ServerLevel resolve(MinecraftServer server) {
        String normalized = TeleportConfig.normalizeWorld(world);
        ResourceKey<Level> key = switch (normalized.toLowerCase(java.util.Locale.ROOT)) {
            case "world" -> Level.OVERWORLD;
            case "world_nether" -> Level.NETHER;
            case "world_the_end" -> Level.END;
            default -> ResourceKey.create(Registries.DIMENSION, Identifier.parse(world));
        };
        return server.getLevel(key);
    }

    public double distanceTo(TeleportPosition other) {
        if (other == null || !TeleportConfig.normalizeWorld(world).equalsIgnoreCase(TeleportConfig.normalizeWorld(other.world))) {
            return 0.0;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
