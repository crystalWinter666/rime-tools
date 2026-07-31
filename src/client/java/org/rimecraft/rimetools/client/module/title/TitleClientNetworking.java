package org.rimecraft.rimetools.client.module.title;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.rimecraft.rimetools.module.title.network.TitlePayloads;

import java.util.function.Consumer;

public final class TitleClientNetworking {
    private static Consumer<TitlePayloads.TitlesResponse> responseConsumer = response -> {
    };
    private static Consumer<TitlePayloads.OperationResult> resultConsumer = result -> {
    };

    private TitleClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TitlePayloads.TitlesResponse.TYPE, (payload, context) ->
                context.client().execute(() -> responseConsumer.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(TitlePayloads.OperationResult.TYPE, (payload, context) ->
                context.client().execute(() -> resultConsumer.accept(payload)));
    }

    public static void setConsumers(Consumer<TitlePayloads.TitlesResponse> responses,
                                    Consumer<TitlePayloads.OperationResult> results) {
        responseConsumer = responses;
        resultConsumer = results;
    }

    public static boolean requestTitles() {
        if (!ClientPlayNetworking.canSend(TitlePayloads.RequestTitles.TYPE)) {
            return false;
        }
        ClientPlayNetworking.send(new TitlePayloads.RequestTitles(TitlePayloads.PROTOCOL_VERSION));
        return true;
    }

    public static void selectTitle(String titleId) {
        send(new TitlePayloads.SelectTitle(titleId));
    }

    public static void saveTitle(String id, String displayName, String color, int weight, boolean enabled) {
        send(new TitlePayloads.UpsertTitle(id, displayName, color, weight, enabled));
    }

    public static void deleteTitle(String titleId) {
        send(new TitlePayloads.DeleteTitle(titleId));
    }

    public static void assignTitle(String playerName, String titleId, boolean granted) {
        send(new TitlePayloads.AssignTitle(playerName, titleId, granted));
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
