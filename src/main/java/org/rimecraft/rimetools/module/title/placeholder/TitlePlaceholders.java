package org.rimecraft.rimetools.module.title.placeholder;

import org.rimecraft.rimetools.RimeTools;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.module.title.TitleModule;
import org.rimecraft.rimetools.module.title.chat.TitleChatFormatter;
import org.rimecraft.rimetools.module.title.storage.TitleRepository;
import org.rimecraft.rimetools.module.title.title.TitleDefinition;

import java.util.Optional;

public final class TitlePlaceholders {
    private TitlePlaceholders() {
    }

    public static void register() {
        Placeholders.registerServer(id(TitleModule.ID), (context, argument) ->
                PlaceholderResult.value(resolve(context).map(TitleDefinition::asComponent)
                        .orElseGet(() -> fallback(context))));
        if (TitleModule.isLuckPermsAvailable()) {
            Placeholders.registerServer(id("title_id"), (context, argument) ->
                    PlaceholderResult.value(resolve(context).map(TitleDefinition::id).orElse("")));
        }
        Placeholders.registerServer(id("title_decorated"), (context, argument) -> {
            Component title = resolve(context).map(TitleDefinition::asComponent)
                    .orElseGet(() -> fallback(context));
            return PlaceholderResult.value(TitleChatFormatter.decorateTitle(title));
        });
    }

    private static Optional<TitleDefinition> resolve(ServerPlaceholderContext context) {
        TitleRepository repository = TitleModule.repository();
        if (repository == null || !context.hasServerPlayer()) {
            return Optional.empty();
        }
        return repository.findVisibleTitle(context.serverPlayer(), TitleModule.permissionChecker());
    }

    private static Component fallback(ServerPlaceholderContext context) {
        TitleRepository repository = TitleModule.repository();
        return repository == null ? Component.literal("玩家") : repository.fallbackComponent();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, path);
    }
}
