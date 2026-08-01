package org.rimecraft.rimetools.client.module.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.client.module.ClientModuleContext;
import org.rimecraft.rimetools.client.module.RimeClientModule;
import org.rimecraft.rimetools.client.module.teleport.config.ClientConfig;
import org.rimecraft.rimetools.client.module.teleport.screen.WaypointManagerScreen;
import org.rimecraft.rimetools.client.module.title.TitleScreen;
import org.rimecraft.rimetools.client.module.teleport.toast.TpaToastManager;
import org.rimecraft.rimetools.client.ui.ClientGuiRegistry;
import org.rimecraft.rimetools.module.teleport.TeleportModule;
import org.rimecraft.rimetools.module.teleport.network.OpenWaypointScreenPayload;
import org.rimecraft.rimetools.module.teleport.network.TpaResultPayload;
import org.rimecraft.rimetools.module.teleport.network.TpaToastPayload;

public final class TeleportClientModule implements RimeClientModule {

    private final TpaToastManager toastManager = new TpaToastManager();
    private final KeyMapping openWaypointScreenKey = new KeyMapping("rime-tools.teleport.key.open_waypoints",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KeyMapping.Category.MULTIPLAYER);

    @Override
    public String id() {
        return TeleportModule.ID;
    }

    @Override
    public void initializeClient(ClientModuleContext context) {
        ClientGuiRegistry.register(TeleportModule.ID,
                Component.translatable("rime-tools.module.teleport"),
                () -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.connection.sendCommand("rime gui");
                    }
                });

        KeyMappingHelper.registerKeyMapping(openWaypointScreenKey);
        KeyMappingHelper.registerKeyMapping(toastManager.keyAccept);
        KeyMappingHelper.registerKeyMapping(toastManager.keyDeny);

        ClientPlayNetworking.registerGlobalReceiver(OpenWaypointScreenPayload.TYPE,
                (p, ctx) -> ctx.client().execute(() -> {
                    if (ctx.client().gui.screen() instanceof WaypointManagerScreen screen) {
                        screen.refreshData(p);
                    } else {
                        ctx.client().setScreenAndShow(new WaypointManagerScreen(p));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(TpaToastPayload.TYPE,
                (p, ctx) -> ctx.client().execute(() -> {
                    if (ClientConfig.get().showToast()) {
                        if (p.requestType() == TpaToastPayload.TYPE_AUTO) {
                            toastManager.addAutoAccepted(p.senderName(), p.requestType());
                        } else if (p.sent()) {
                            toastManager.addSent(p.senderName(), p.requestType(), p.timeoutSeconds());
                        } else {
                            toastManager.addIncoming(p.senderName(), p.requestType(), p.timeoutSeconds());
                        }
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(TpaResultPayload.TYPE,
                (p, ctx) -> ctx.client().execute(() ->
                        toastManager.markResult(p.otherName(), p.accepted())));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            toastManager.tick();
            if (openWaypointScreenKey.consumeClick() && client.player != null) {
                client.player.connection.sendCommand("rime gui");
            }
            if (toastManager.keyAccept.consumeClick()) {
                var t = toastManager.oldestIncoming();
                if (t != null && client.player != null)
                    client.player.connection.sendCommand("rime accept " + t.name);
            }
            if (toastManager.keyDeny.consumeClick()) {
                var t = toastManager.oldestIncoming();
                if (t != null && client.player != null)
                    client.player.connection.sendCommand("rime deny " + t.name);
            }
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "tpa_toast"), toastManager);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, reg) -> {
            dispatcher.register(ClientCommands.literal("rimenotify").executes(ctx -> {
                var cfg = ClientConfig.get();
                cfg.cycleStyle();
                ctx.getSource().sendFeedback(Component.literal("\u00a76[RIME]\u00a7r TPA: \u00a7e" + cfg.tpaNotificationStyle));
                return 1;
            }));
            dispatcher.register(ClientCommands.literal("rimetest")
                    .then(ClientCommands.literal("tpa")
                            .then(ClientCommands.argument("player", StringArgumentType.word())
                                    .then(ClientCommands.argument("type", IntegerArgumentType.integer(0, 1))
                                            .executes(ctx -> {
                                                String n = StringArgumentType.getString(ctx, "player");
                                                int t = IntegerArgumentType.getInteger(ctx, "type");
                                                toastManager.addIncoming(n, t, 60);
                                                toastManager.addSent("Me", t, 60);
                                                ctx.getSource().sendFeedback(Component.literal("\u00a7a[Test] TPA from \u00a7e" + n));
                                                return 1;
                                            }))
                                    .executes(ctx -> {
                                        String n = StringArgumentType.getString(ctx, "player");
                                        toastManager.addIncoming(n, 0, 60);
                                        toastManager.addSent("Me", 0, 60);
                                        ctx.getSource().sendFeedback(Component.literal("\u00a7a[Test] TPA from \u00a7e" + n));
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("result")
                            .then(ClientCommands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        toastManager.markResult(StringArgumentType.getString(ctx, "player"), true);
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("toast").executes(ctx -> {
                        toastManager.addIncoming("TestPlayer", 0, 60);
                        toastManager.addSent("AnotherPlayer", 1, 30);
                        ctx.getSource().sendFeedback(Component.literal("\u00a7a[Test] 2 toasts"));
                        return 1;
                    }))
                    .then(ClientCommands.literal("gui").executes(ctx -> {
                        var player = Minecraft.getInstance().player;
                        if (player == null) return 0;
                        // Seeds the test waypoint server-side (idempotent) and opens the teleport
                        // GUI listing it, so the screen is never empty in a fresh singleplayer world.
                        player.connection.sendCommand("rime test wp");
                        ctx.getSource().sendFeedback(Component.literal("\u00a7a[Test] Sent rime test wp; teleport GUI should open with test_wp (admin required)"));
                        return 1;
                    }))
                    .then(ClientCommands.literal("title").executes(ctx -> {
                        var player = Minecraft.getInstance().player;
                        if (player == null) return 0;
                        // Seeds the three test titles server-side before the screen requests them.
                        player.connection.sendCommand("rime test title");
                        Minecraft.getInstance().setScreenAndShow(new TitleScreen(null));
                        ctx.getSource().sendFeedback(Component.literal("\u00a7a[Test] Sent rime test title; opened title GUI"));
                        return 1;
                    })));
        });
    }
}
