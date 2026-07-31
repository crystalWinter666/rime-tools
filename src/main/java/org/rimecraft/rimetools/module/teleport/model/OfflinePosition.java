package org.rimecraft.rimetools.module.teleport.model;

public record OfflinePosition(
        String playerName,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long updatedAt
) {
    public TeleportPosition position() {
        return new TeleportPosition(world, x, y, z, yaw, pitch);
    }
}
