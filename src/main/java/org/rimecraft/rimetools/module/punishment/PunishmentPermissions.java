package org.rimecraft.rimetools.module.punishment;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.rimecraft.rimetools.RimeTools;

/** Permission nodes shared by commands, chat handling and the administration GUI. */
public final class PunishmentPermissions {
    public static final Identifier ROOT = RimeTools.id("punish");
    public static final Identifier APPLY = RimeTools.id("punish/apply");
    public static final Identifier REVOKE = RimeTools.id("punish/revoke");
    public static final Identifier HISTORY = RimeTools.id("punish/history");
    public static final Identifier CLEAR_RANK = RimeTools.id("punish/clearrank");
    public static final Identifier RELOAD = RimeTools.id("punish/reload");
    public static final Identifier CHAT_BYPASS = RimeTools.id("chat/bypass");

    private PunishmentPermissions() { }

    public static boolean has(CommandSourceStack source, Identifier node) {
        PermissionContextOwner owner = (PermissionContextOwner) (Object) source;
        return owner.checkPermission(ROOT, PermissionLevel.ADMINS)
                || owner.checkPermission(node, PermissionLevel.ADMINS);
    }

    public static boolean has(ServerPlayer player, Identifier node) {
        PermissionContextOwner owner = (PermissionContextOwner) (Object) player.createCommandSourceStack();
        return owner.checkPermission(ROOT, PermissionLevel.ADMINS)
                || owner.checkPermission(node, PermissionLevel.ADMINS);
    }

    public static boolean chatBypass(ServerPlayer player) {
        return has(player, CHAT_BYPASS);
    }
}
