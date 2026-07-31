package org.rimecraft.rimetools.module.teleport.teleport;

import org.rimecraft.rimetools.module.teleport.manager.CooldownManager;

public enum TeleportType {
    WAYPOINT_PERSONAL(CooldownManager.Type.WAYPOINT),
    WAYPOINT_GLOBAL(CooldownManager.Type.WAYPOINT),
    FAKE_PLAYER(CooldownManager.Type.WAYPOINT),
    TP(CooldownManager.Type.TP),
    TPHERE(CooldownManager.Type.TP),
    TPA(CooldownManager.Type.TP),
    TPAHERE(CooldownManager.Type.TP),
    BACK(CooldownManager.Type.BACK),
    RANDOM(CooldownManager.Type.RANDOM);

    private final CooldownManager.Type cooldownType;

    TeleportType(CooldownManager.Type cooldownType) {
        this.cooldownType = cooldownType;
    }

    public CooldownManager.Type cooldownType() {
        return cooldownType;
    }
}
