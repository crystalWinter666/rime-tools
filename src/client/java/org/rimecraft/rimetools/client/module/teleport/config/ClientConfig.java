package org.rimecraft.rimetools.client.module.teleport.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("rime-tools").resolve("teleport-client.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig instance;
    public String tpaNotificationStyle = "BOTH"; // TOAST, CHAT, BOTH

    public static ClientConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static ClientConfig load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) {
                return GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ClientConfig.class);
            }
        } catch (IOException e) {
            // fall through to defaults
        }
        ClientConfig cfg = new ClientConfig();
        cfg.save();
        return cfg;
    }

    public boolean showToast() {
        return "TOAST".equals(tpaNotificationStyle) || "BOTH".equals(tpaNotificationStyle);
    }

    public boolean showChat() {
        return "CHAT".equals(tpaNotificationStyle) || "BOTH".equals(tpaNotificationStyle);
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public void cycleStyle() {
        tpaNotificationStyle = switch (tpaNotificationStyle) {
            case "TOAST" -> "CHAT";
            case "CHAT" -> "BOTH";
            default -> "TOAST";
        };
        save();
    }
}
