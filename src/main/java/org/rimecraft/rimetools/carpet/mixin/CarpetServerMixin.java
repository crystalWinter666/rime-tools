package org.rimecraft.rimetools.carpet.mixin;

import carpet.CarpetServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.carpet.CarpetPermissions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(value = CarpetServer.class, remap = false)
public abstract class CarpetServerMixin {
    @Unique
    private static final ThreadLocal<Set<CommandNode<CommandSourceStack>>> rimetools$existingRoots = new ThreadLocal<>();

    @Inject(method = "registerCarpetCommands", at = @At("HEAD"), remap = false)
    private static void rimetools$captureExistingCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            Commands.CommandSelection environment,
            CommandBuildContext commandBuildContext,
            CallbackInfo callbackInfo
    ) {
        Set<CommandNode<CommandSourceStack>> roots = Collections.newSetFromMap(new IdentityHashMap<>());
        roots.addAll(dispatcher.getRoot().getChildren());
        rimetools$existingRoots.set(roots);
    }

    @Inject(method = "registerCarpetCommands", at = @At("RETURN"), remap = false)
    private static void rimetools$addPermissions(
            CommandDispatcher<CommandSourceStack> dispatcher,
            Commands.CommandSelection environment,
            CommandBuildContext commandBuildContext,
            CallbackInfo callbackInfo
    ) {
        Set<CommandNode<CommandSourceStack>> existingRoots = rimetools$existingRoots.get();
        rimetools$existingRoots.remove();
        if (existingRoots == null) {
            RimeTools.LOGGER.error("Could not determine which commands were registered by Carpet");
            return;
        }

        int commandTrees = 0;
        int permissionNodes = 0;
        for (CommandNode<CommandSourceStack> root : dispatcher.getRoot().getChildren()) {
            if (!existingRoots.contains(root)) {
                commandTrees++;
                permissionNodes += CarpetPermissions.wrap(root);
            }
        }

        RimeTools.LOGGER.info(
                "Registered {} Carpet command trees with {} permission nodes",
                commandTrees,
                permissionNodes
        );
    }
}
