package org.rimecraft.rimetools.client.module.punishment;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.rimecraft.rimetools.module.punishment.network.PunishmentPayloads;

import java.util.function.Consumer;

public final class PunishmentClientNetworking {
    private static Consumer<PunishmentPayloads.Response> responseConsumer = ignored -> { };
    private static Consumer<PunishmentPayloads.Result> resultConsumer = ignored -> { };
    private PunishmentClientNetworking() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PunishmentPayloads.Response.TYPE, (payload, context) ->
                context.client().execute(() -> responseConsumer.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PunishmentPayloads.Result.TYPE, (payload, context) ->
                context.client().execute(() -> resultConsumer.accept(payload)));
    }

    public static void setConsumers(Consumer<PunishmentPayloads.Response> response,
                                    Consumer<PunishmentPayloads.Result> result) {
        responseConsumer = response;
        resultConsumer = result;
    }

    public static boolean request(String query, int page) {
        if (!ClientPlayNetworking.canSend(PunishmentPayloads.Request.TYPE)) return false;
        ClientPlayNetworking.send(new PunishmentPayloads.Request(PunishmentPayloads.PROTOCOL_VERSION, query, page));
        return true;
    }

    public static void action(String playerUuid, String playerName, String action,
                              long duration, String reason, String recordId) {
        if (ClientPlayNetworking.canSend(PunishmentPayloads.Action.TYPE)) {
            ClientPlayNetworking.send(new PunishmentPayloads.Action(playerUuid, playerName, action,
                    duration, reason, recordId));
        }
    }
}
