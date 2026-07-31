package org.rimecraft.rimetools.client.module.punishment;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.rimecraft.rimetools.RimeTools;
import org.rimecraft.rimetools.client.module.ClientModuleContext;
import org.rimecraft.rimetools.client.module.RimeClientModule;
import org.rimecraft.rimetools.module.punishment.PunishmentModule;
import org.rimecraft.rimetools.module.punishment.network.MuteNoticePayload;

public final class PunishmentClientModule implements RimeClientModule {
    private final MuteToast muteToast = new MuteToast();

    @Override
    public String id() {
        return PunishmentModule.ID;
    }

    @Override
    public void initializeClient(ClientModuleContext context) {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(RimeTools.MOD_ID, "mute_notice"), muteToast);
        ClientPlayNetworking.registerGlobalReceiver(MuteNoticePayload.TYPE,
                (p, ctx) -> ctx.client().execute(() -> muteToast.show(p.remainingSeconds())));
    }
}
