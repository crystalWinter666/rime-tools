package org.rimecraft.rimetools.carpet.mixin;

import carpet.commands.PlayerCommand;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.rimecraft.rimetools.carpet.fakeplayer.FakePlayerCreatorRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PlayerCommand.class, remap = false)
public abstract class PlayerCommandMixin {
    @Redirect(
            method = "spawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lcarpet/patches/EntityPlayerMPFake;createFake(Ljava/lang/String;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/phys/Vec3;DDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/GameType;Z)Z"
            ),
            remap = false
    )
    private static boolean rimetools$recordFakePlayerCreator(
            String name,
            MinecraftServer server,
            Vec3 position,
            double yaw,
            double pitch,
            ResourceKey<Level> dimension,
            GameType gameMode,
            boolean flying,
            CommandContext<CommandSourceStack> context
    ) {
        boolean created = EntityPlayerMPFake.createFake(
                name,
                server,
                position,
                yaw,
                pitch,
                dimension,
                gameMode,
                flying
        );
        if (created) {
            FakePlayerCreatorRegistry.recordPending(name, context.getSource());
        }
        return created;
    }

    @Redirect(
            method = "shadow",
            at = @At(
                    value = "INVOKE",
                    target = "Lcarpet/patches/EntityPlayerMPFake;createShadow(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)Lcarpet/patches/EntityPlayerMPFake;"
            ),
            remap = false
    )
    private static EntityPlayerMPFake rimetools$recordShadowCreator(
            MinecraftServer server,
            ServerPlayer player,
            CommandContext<CommandSourceStack> context
    ) {
        FakePlayerCreatorRegistry.recordPending(player.getGameProfile().name(), context.getSource());
        return EntityPlayerMPFake.createShadow(server, player);
    }
}
