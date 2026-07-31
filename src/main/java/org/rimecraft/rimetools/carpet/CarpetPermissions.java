package org.rimecraft.rimetools.carpet;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.commands.CommandSourceStack;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.carpet.mixin.CommandNodeAccessor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Wraps every Carpet command node (including extensions) with a
 * {@code carpet.command.<path>} permission node via Fabric Permission API.
 * Unset nodes fall back to Carpet's original rule/OP checks.
 */
public final class CarpetPermissions {
    private static final String PERMISSION_NAMESPACE = "carpet";
    private static final String COMMAND_PREFIX = "command.";

    private CarpetPermissions() {
    }

    public static int wrap(CommandNode<CommandSourceStack> root) {
        Set<CommandNode<CommandSourceStack>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return wrap(root, sanitize(root.getName()), visited);
    }

    private static int wrap(
            CommandNode<CommandSourceStack> node,
            String path,
            Set<CommandNode<CommandSourceStack>> visited
    ) {
        if (!visited.add(node)) {
            return 0;
        }

        PermissionNode<Boolean> permission = PermissionNode.of(PERMISSION_NAMESPACE, COMMAND_PREFIX + path);
        Predicate<CommandSourceStack> originalRequirement = node.getRequirement();
        Predicate<CommandSourceStack> permissionRequirement = source ->
                ((PermissionContextOwner) (Object) source).checkPermission(
                        permission,
                        originalRequirement.test(source)
                );

        @SuppressWarnings("unchecked")
        CommandNodeAccessor<CommandSourceStack> accessor = (CommandNodeAccessor<CommandSourceStack>) (Object) node;
        accessor.rimetools$setRequirement(permissionRequirement);

        int wrapped = 1;
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            wrapped += wrap(child, path + "." + sanitize(child.getName()), visited);
        }
        return wrapped;
    }

    private static String sanitize(String segment) {
        String lowerCase = segment.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lowerCase.length());
        for (int index = 0; index < lowerCase.length(); index++) {
            char character = lowerCase.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_'
                    || character == '-') {
                result.append(character);
            } else {
                result.append('_');
            }
        }

        if (result.isEmpty()) {
            RimeTools.LOGGER.warn("Encountered an unnamed Carpet command node");
            return "unnamed";
        }
        return result.toString();
    }
}
