package org.rimecraft.rimetools.module.teleport.network;

import java.util.UUID;

public record TeleportPlayerTarget(UUID uuid, String name, boolean online, boolean tpaAllowed) {
}
