package org.rimecraft.rimetools.client.module.teleport.toast;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.UUID;

public final class TpaToastManager implements HudElement {

    private static final int WIDTH = 196;
    private static final int HEIGHT = 54;
    private static final int GAP = 5;
    private static final int ENTER_TICKS = 10;
    private static final int REQUEST_EXIT_TICKS = 9;
    private static final int RESULT_HOLD_TICKS = 18;
    private static final int RESULT_EXIT_TICKS = 5;

    public final KeyMapping keyAccept;
    public final KeyMapping keyDeny;
    private final LinkedList<ToastEntry> toasts = new LinkedList<>();

    public TpaToastManager() {
        keyAccept = new KeyMapping("rime-tools.teleport.key.tpa_accept",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, KeyMapping.Category.MULTIPLAYER);
        keyDeny = new KeyMapping("rime-tools.teleport.key.tpa_deny",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, KeyMapping.Category.MULTIPLAYER);
    }

    private static void renderAvatar(GuiGraphicsExtractor graphics, Minecraft minecraft, ToastEntry toast,
                                     int x, int y, int accent, float visibility) {
        roundedRect(graphics, x - 1, y - 1, 26, 26, color(0xFF252C34, visibility));
        PlayerFaceExtractor.extractRenderState(graphics, playerSkin(minecraft, toast.name),
                x + 1, y + 1, 22, color(0xFFFFFFFF, visibility));
        roundedOutline(graphics, x - 1, y - 1, 26, 26, color(0xFF424B55, visibility));

        graphics.fill(x + 18, y + 18, x + 25, y + 25, color(0xFF11151A, visibility));
        graphics.fill(x + 20, y + 20, x + 23, y + 23, color(accent, visibility));
    }

    private static PlayerSkin playerSkin(Minecraft minecraft, String name) {
        var connection = minecraft.getConnection();
        if (connection != null) {
            var info = connection.getPlayerInfoIgnoreCase(name);
            if (info != null) return info.getSkin();
        }
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return DefaultPlayerSkin.get(offlineUuid);
    }

    private static int drawKeyAction(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y,
                                     Component key, Component label, int accent, float visibility) {
        int keyWidth = minecraft.font.width(key) + 7;
        int labelWidth = minecraft.font.width(label);
        roundedRect(graphics, x, y - 1, keyWidth, 13, color(0xFF252C34, visibility));
        roundedOutline(graphics, x, y - 1, keyWidth, 13, color(accent, visibility));
        graphics.centeredText(minecraft.font, key, x + keyWidth / 2, y + 2, color(accent, visibility));
        graphics.text(minecraft.font, label, x + keyWidth + 4, y + 1,
                color(0xFF929DA8, visibility), false);
        return keyWidth + labelWidth + 4;
    }

    private static int accentColor(ToastEntry toast) {
        return switch (toast.state) {
            case ACCEPTED, AUTO -> 0xFF4ADE80;
            case DENIED -> 0xFFFB7185;
            case SENT -> 0xFF60A5FA;
            case INCOMING -> toast.type == 0 ? 0xFF38BDF8 : 0xFFFBBF24;
        };
    }

    private static int exitTicks(ToastEntry toast) {
        return toast.state == State.ACCEPTED || toast.state == State.DENIED || toast.state == State.AUTO
                ? RESULT_EXIT_TICKS
                : REQUEST_EXIT_TICKS;
    }

    private static String bodyKey(ToastEntry toast) {
        return switch (toast.state) {
            case ACCEPTED -> "rime-tools.teleport.toast.accepted";
            case DENIED -> "rime-tools.teleport.toast.denied";
            case SENT -> "rime-tools.teleport.toast.sent";
            case AUTO -> "rime-tools.teleport.toast.auto_accepted";
            case INCOMING -> toast.type == 0
                    ? "rime-tools.teleport.toast.wants_tp"
                    : "rime-tools.teleport.toast.wants_here";
        };
    }

    private static String statusKey(ToastEntry toast) {
        return switch (toast.state) {
            case ACCEPTED -> "rime-tools.teleport.toast.accepted_title";
            case DENIED -> "rime-tools.teleport.toast.denied_title";
            case SENT -> "rime-tools.teleport.toast.waiting";
            case AUTO -> "rime-tools.teleport.toast.auto_title";
            case INCOMING -> "rime-tools.teleport.toast.incoming_title";
        };
    }

    private static String trim(Minecraft minecraft, String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        return minecraft.font.plainSubstrByWidth(text,
                Math.max(0, maxWidth - minecraft.font.width(ellipsis))) + ellipsis;
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0f - value;
        return 1.0f - inverse * inverse * inverse;
    }

    private static int color(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        return (Mth.clamp((int) (alpha * opacity), 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    private static void roundedRect(GuiGraphicsExtractor graphics, int x, int y,
                                    int width, int height, int color) {
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    private static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y,
                                       int width, int height, int color) {
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + 1, y + height - 2, color);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    public void addIncoming(String name, int type, int seconds) {
        replace(new ToastEntry(name, type, seconds, State.INCOMING));
    }

    public void addSent(String name, int type, int seconds) {
        replace(new ToastEntry(name, type, seconds, State.SENT));
    }

    /**
     * Shows a short, button-less notice for an automatically accepted teleport.
     */
    public void addAutoAccepted(String name, int type) {
        replace(new ToastEntry(name, type, 3, State.AUTO));
    }

    private void replace(ToastEntry entry) {
        toasts.removeIf(toast -> toast.name.equals(entry.name));
        toasts.addFirst(entry);
        while (toasts.size() > 3) toasts.removeLast();
    }

    public void markResult(String name, boolean accepted) {
        for (ToastEntry toast : toasts) {
            if (toast.name.equals(name)) {
                toast.state = accepted ? State.ACCEPTED : State.DENIED;
                // Keep the card in place, briefly show the result, then retract quickly.
                toast.age = Math.max(toast.age, ENTER_TICKS);
                toast.phaseStartedAt = toast.age;
                toast.duration = toast.age + RESULT_HOLD_TICKS;
                return;
            }
        }
    }

    public void tick() {
        var iterator = toasts.iterator();
        while (iterator.hasNext()) {
            ToastEntry toast = iterator.next();
            toast.age++;
            if (toast.age > toast.duration + exitTicks(toast)) iterator.remove();
        }
    }

    public ToastEntry oldestIncoming() {
        for (ToastEntry toast : toasts) {
            if (toast.state == State.INCOMING) return toast;
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (toasts.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = Math.min(WIDTH, screenWidth - 16);
        int visibleCount = Math.min(3, toasts.size());
        int stackHeight = visibleCount * HEIGHT + (visibleCount - 1) * GAP;
        int top = (screenHeight - stackHeight) / 2;

        int index = 0;
        for (ToastEntry toast : toasts) {
            if (index >= visibleCount) break;
            renderToast(graphics, minecraft, toast, width, top + index * (HEIGHT + GAP));
            index++;
        }
    }

    private void renderToast(GuiGraphicsExtractor graphics, Minecraft minecraft, ToastEntry toast,
                             int width, int y) {
        float enter = easeOutCubic(Mth.clamp(toast.age / (float) ENTER_TICKS, 0.0f, 1.0f));
        float exit = Mth.clamp((toast.age - toast.duration) / (float) exitTicks(toast), 0.0f, 1.0f);
        float visibility = enter * (1.0f - exit);
        int targetX = 8;
        int x = (int) Mth.lerp(enter, -width - 6, targetX) - (int) (exit * (width + 14));
        int accent = accentColor(toast);

        roundedRect(graphics, x + 3, y + 3, width, HEIGHT, color(0x55000000, visibility));
        roundedRect(graphics, x, y, width, HEIGHT, color(0xF211151A, visibility));
        roundedOutline(graphics, x, y, width, HEIGHT, color(0xFF303840, visibility));
        graphics.fill(x, y + 3, x + 2, y + HEIGHT - 3, color(accent, visibility));

        renderAvatar(graphics, minecraft, toast, x + 9, y + 9, accent, visibility);

        int textX = x + 42;
        int textRight = x + width - 9;
        boolean timed = toast.state == State.INCOMING || toast.state == State.SENT;
        String countdown = timed ? Math.max(0, (toast.duration - toast.age + 19) / 20) + "s" : "";
        int nameWidth = textRight - textX - (timed ? minecraft.font.width(countdown) + 9 : 0);
        graphics.text(minecraft.font, trim(minecraft, toast.name, nameWidth), textX, y + 7,
                color(0xFFF2F5F7, visibility), false);
        if (timed) {
            graphics.text(minecraft.font, countdown, textRight - minecraft.font.width(countdown), y + 7,
                    color(0xFF788491, visibility), false);
        }

        String body = Component.translatable(bodyKey(toast)).getString();
        graphics.text(minecraft.font, trim(minecraft, body, textRight - textX), textX, y + 20,
                color(0xFFADB7C1, visibility), false);

        if (toast.state == State.INCOMING) {
            int acceptWidth = drawKeyAction(graphics, minecraft, textX, y + 37,
                    keyAccept.getTranslatedKeyMessage(), Component.translatable("rime-tools.teleport.toast.accept"),
                    0xFF4ADE80, visibility);
            drawKeyAction(graphics, minecraft, textX + acceptWidth + 7, y + 37,
                    keyDeny.getTranslatedKeyMessage(), Component.translatable("rime-tools.teleport.toast.deny"),
                    0xFFFB7185, visibility);
        } else {
            graphics.text(minecraft.font, Component.translatable(statusKey(toast)), textX, y + 37,
                    color(accent, visibility), false);
        }

        int progressStart = toast.state == State.ACCEPTED || toast.state == State.DENIED || toast.state == State.AUTO
                ? toast.phaseStartedAt
                : ENTER_TICKS;
        float remaining = toast.age <= progressStart ? 1.0f
                : 1.0f - Mth.clamp((toast.age - progressStart)
                                   / (float) Math.max(1, toast.duration - progressStart), 0.0f, 1.0f);
        int progressWidth = (int) ((width - 4) * remaining);
        if (progressWidth > 0) {
            graphics.fill(x + 2, y + HEIGHT - 2, x + 2 + progressWidth, y + HEIGHT,
                    color(accent, visibility * 0.8f));
        }
    }

    enum State {INCOMING, SENT, ACCEPTED, DENIED, AUTO}

    public static final class ToastEntry {
        public final String name;
        public final int type;
        int duration;
        int age;
        int phaseStartedAt;
        State state;

        ToastEntry(String name, int type, int seconds, State state) {
            this.name = name;
            this.type = type;
            this.duration = Math.max(1, seconds * 20);
            this.state = state;
        }
    }
}
