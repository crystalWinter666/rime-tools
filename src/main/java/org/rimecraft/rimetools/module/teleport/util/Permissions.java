package org.rimecraft.rimetools.module.teleport.util;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.rimecraft.rimetools.RimeTools;

public final class Permissions {
    private static final Identifier ADMIN = id("admin");

    private Permissions() {
    }

    public static boolean has(ServerPlayer player, String node, boolean publicDefault) {
        PermissionContextOwner owner = (PermissionContextOwner) (Object) player;
        return owner.checkPermission(ADMIN, PermissionLevel.ADMINS)
                || owner.checkPermission(id(normalize(node)), publicDefault);
    }

    public static boolean has(CommandSourceStack source, String node, boolean publicDefault) {
        PermissionContextOwner owner = (PermissionContextOwner) (Object) source;
        return owner.checkPermission(ADMIN, PermissionLevel.ADMINS)
                || owner.checkPermission(id(normalize(node)), publicDefault);
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return ((PermissionContextOwner) (Object) source).checkPermission(ADMIN, PermissionLevel.ADMINS);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, path);
    }

    private static String normalize(String node) {
        String value = node;
        String prefix = "rime-tools.teleport.";
        if (value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        return value.replace('.', '/');
    }
}
