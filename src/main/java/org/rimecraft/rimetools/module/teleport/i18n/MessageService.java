package org.rimecraft.rimetools.module.teleport.i18n;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.rimecraft.rimetools.module.teleport.config.TeleportConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MessageService {
    private final Path dataDirectory;
    private final String defaultLocale;
    private final Map<String, String> zh = new HashMap<>();
    private final Map<String, String> en = new HashMap<>();

    public MessageService(Path dataDirectory, String defaultLocale) {
        this.dataDirectory = dataDirectory;
        this.defaultLocale = defaultLocale == null ? "en_US" : defaultLocale;
    }

    public void load() throws Exception {
        zh.clear();
        en.clear();
        loadInto("messages_zh_CN.yml", zh);
        loadInto("messages_en_US.yml", en);
    }

    private void loadInto(String fileName, Map<String, String> target) throws Exception {
        try (InputStream defaults = MessageService.class.getResourceAsStream("/teleport/" + fileName)) {
            if (defaults != null) {
                flatten("", new Yaml().load(defaults), target);
            }
        }
        Path file = dataDirectory.resolve(fileName);
        TeleportConfig.copyDefault(fileName, file);
        try (InputStream input = Files.newInputStream(file)) {
            flatten("", new Yaml().load(input), target);
        }
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Object value, Map<String, String> target) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                flatten(key, entry.getValue(), target);
            }
        } else if (value != null) {
            target.put(prefix, String.valueOf(value));
        }
    }

    public void send(CommandSourceStack source, String key, Map<String, ?> variables) {
        source.sendSystemMessage(component(source.getPlayer(), key, variables));
    }

    public void send(CommandSourceStack source, String key) {
        send(source, key, Map.of());
    }

    public void send(ServerPlayer player, String key, Map<String, ?> variables) {
        player.sendSystemMessage(component(player, key, variables));
    }

    public void send(ServerPlayer player, String key) {
        send(player, key, Map.of());
    }

    public Component component(ServerPlayer player, String key) {
        return component(player, key, Map.of());
    }

    public Component component(ServerPlayer player, String key, Map<String, ?> variables) {
        String raw = resolveMessage(player, key);
        if (raw == null || raw.isEmpty()) {
            return Component.literal("[" + key + "]");
        }

        boolean noPrefix = "prefix".equalsIgnoreCase(key) || raw.startsWith("<noprefix>") || raw.contains("<prefix>");
        if (raw.startsWith("<noprefix>")) {
            raw = raw.substring("<noprefix>".length()).trim();
        }

        Map<String, Object> expanded = new LinkedHashMap<>(variables);
        if (raw.contains("<prefix>")) {
            expanded.put("prefix", parse(resolveMessage(player, "prefix"), Map.of()));
        }
        MutableComponent body = parse(raw, expanded);
        if (noPrefix) {
            return body;
        }
        String prefixRaw = resolveMessage(player, "prefix");
        if (prefixRaw == null || prefixRaw.startsWith("[")) {
            return body;
        }
        return parse(prefixRaw, Map.of()).append(body);
    }

    public Component button(ServerPlayer player, String labelKey, String command, String hoverKey, boolean suggest) {
        Component label = component(player, labelKey);
        Component hover = component(player, hoverKey);
        ClickEvent click = suggest ? new ClickEvent.SuggestCommand(command) : new ClickEvent.RunCommand(command);
        return label.copy().withStyle(style -> style.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    public String resolveMessage(ServerPlayer player, String key) {
        String locale = defaultLocale;
        if (player != null) {
            String language = player.clientInformation().language();
            if (language != null && language.toLowerCase(Locale.ROOT).startsWith("zh")) {
                locale = "zh_CN";
            } else if (language != null && language.toLowerCase(Locale.ROOT).startsWith("en")) {
                locale = "en_US";
            }
        }
        Map<String, String> bundle = "zh_CN".equalsIgnoreCase(locale) ? zh : en;
        String value = bundle.get(key);
        if (value != null) {
            return value;
        }
        Map<String, String> fallback = bundle == zh ? en : zh;
        return fallback.getOrDefault(key, "[" + key + "]");
    }

    public static Map<String, Object> vars(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Variables must be key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private MutableComponent parse(String raw, Map<String, ?> variables) {
        MutableComponent root = Component.empty();
        Deque<Style> styles = new ArrayDeque<>();
        Style current = Style.EMPTY;
        int cursor = 0;
        while (cursor < raw.length()) {
            int open = raw.indexOf('<', cursor);
            if (open < 0) {
                append(root, raw.substring(cursor), current);
                break;
            }
            if (open > cursor) {
                append(root, raw.substring(cursor, open), current);
            }
            int close = raw.indexOf('>', open + 1);
            if (close < 0) {
                append(root, raw.substring(open), current);
                break;
            }
            String token = raw.substring(open + 1, close);
            Object replacement = variables.get(token);
            if (replacement != null) {
                if (replacement instanceof Component component) {
                    root.append(component);
                } else {
                    append(root, String.valueOf(replacement), current);
                }
            } else if (token.startsWith("/")) {
                current = styles.isEmpty() ? Style.EMPTY : styles.pop();
            } else if ("br".equalsIgnoreCase(token) || "newline".equalsIgnoreCase(token)) {
                append(root, "\n", current);
            } else {
                Style updated = applyTag(current, token);
                if (updated != null) {
                    styles.push(current);
                    current = updated;
                }
            }
            cursor = close + 1;
        }
        return root;
    }

    private void append(MutableComponent root, String text, Style style) {
        if (!text.isEmpty()) {
            root.append(Component.literal(text).setStyle(style));
        }
    }

    private Style applyTag(Style current, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        ChatFormatting formatting = switch (lower) {
            case "black" -> ChatFormatting.BLACK;
            case "dark_blue" -> ChatFormatting.DARK_BLUE;
            case "dark_green" -> ChatFormatting.DARK_GREEN;
            case "dark_aqua" -> ChatFormatting.DARK_AQUA;
            case "dark_red" -> ChatFormatting.DARK_RED;
            case "dark_purple" -> ChatFormatting.DARK_PURPLE;
            case "gold" -> ChatFormatting.GOLD;
            case "gray", "grey" -> ChatFormatting.GRAY;
            case "dark_gray", "dark_grey" -> ChatFormatting.DARK_GRAY;
            case "blue" -> ChatFormatting.BLUE;
            case "green" -> ChatFormatting.GREEN;
            case "aqua" -> ChatFormatting.AQUA;
            case "red" -> ChatFormatting.RED;
            case "light_purple" -> ChatFormatting.LIGHT_PURPLE;
            case "yellow" -> ChatFormatting.YELLOW;
            case "white" -> ChatFormatting.WHITE;
            case "bold" -> ChatFormatting.BOLD;
            case "italic" -> ChatFormatting.ITALIC;
            case "underlined" -> ChatFormatting.UNDERLINE;
            case "strikethrough" -> ChatFormatting.STRIKETHROUGH;
            default -> null;
        };
        if (formatting != null) {
            return current.applyFormat(formatting);
        }
        if (lower.startsWith("gradient:")) {
            String first = token.substring("gradient:".length()).split(":", 2)[0];
            TextColor color = parseHex(first);
            return color == null ? current : current.withColor(color);
        }
        if (lower.startsWith("#")) {
            TextColor color = parseHex(token);
            return color == null ? current : current.withColor(color);
        }
        if (lower.startsWith("click:run_command:")) {
            return current.withClickEvent(new ClickEvent.RunCommand(unquote(token.substring("click:run_command:".length()))));
        }
        if (lower.startsWith("click:suggest_command:")) {
            return current.withClickEvent(new ClickEvent.SuggestCommand(unquote(token.substring("click:suggest_command:".length()))));
        }
        return null;
    }

    private TextColor parseHex(String value) {
        try {
            return TextColor.fromRgb(Integer.parseInt(value.replace("#", ""), 16));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\"")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
