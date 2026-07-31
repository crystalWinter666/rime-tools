package org.rimecraft.rimetools.module.title.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.title.TitleModule;

import org.rimecraft.rimetools.module.title.permission.TitlePermissions;
import org.rimecraft.rimetools.module.title.storage.TitleRepository;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;
import org.rimecraft.rimetools.module.title.title.TitleInputValidator;

public final class TitleCommands {
    private TitleCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("title")
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("select")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> select(context.getSource(), StringArgumentType.getString(context, "id"))))));
    }

    private static int list(CommandSourceStack source) {
        TitleRepository repository = TitleModule.repository();
        if (repository == null) {
            source.sendFailure(Component.translatable("rime-tools.title.error.not_ready"));
            return 0;
        }
        repository.state().titles().values().stream()
                .filter(TitleDefinition::enabled)
                .forEach(title -> source.sendSuccess(() -> Component.literal(title.id() + " - " + title.displayName()), false));
        return 1;
    }

    private static int select(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!TitleModule.isLuckPermsAvailable() || !TitleInputValidator.isValidId(id)) {
            source.sendFailure(Component.translatable("rime-tools.title.error.permissions_unavailable"));
            return 0;
        }
        TitleRepository repository = TitleModule.repository();
        if (repository == null) {
            source.sendFailure(Component.translatable("rime-tools.title.error.not_ready"));
            return 0;
        }
        TitleDefinition title = repository.state().titles().get(id);
        if (title == null || !title.enabled()
                || !TitleModule.permissionChecker().has(player, TitlePermissions.title(id))) {
            source.sendFailure(Component.translatable("rime-tools.title.error.title_locked"));
            return 0;
        }
        repository.select(player.getUUID(), id);
        source.sendSuccess(() -> Component.translatable("rime-tools.title.success.selected_named", title.displayName()), false);
        return 1;
    }
}
