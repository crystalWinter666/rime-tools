package org.rimecraft.rimetools.module.teleport.model;

public record FakePlayerInfo(
        String name,
        String world,
        double x,
        double y,
        double z,
        String creatorName,
        boolean ownedByViewer
) {
    public TeleportPosition position() {
        return new TeleportPosition(world, x, y, z, 0.0f, 0.0f);
    }
}
