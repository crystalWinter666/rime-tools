package org.rimecraft.rimetools.module.punishment.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentPayloadValidationTest {
    private static final String PLAYER = UUID.randomUUID().toString();

    @Test void validatesBoundedRequests() {
        assertTrue(PunishmentPayloadValidation.validRequest(
                new PunishmentPayloads.Request(PunishmentPayloads.PROTOCOL_VERSION, "alice", 1)));
        assertFalse(PunishmentPayloadValidation.validRequest(
                new PunishmentPayloads.Request(99, "alice", 1)));
        assertFalse(PunishmentPayloadValidation.validRequest(
                new PunishmentPayloads.Request(PunishmentPayloads.PROTOCOL_VERSION, "x".repeat(65), 1)));
    }

    @Test void validatesActionsAndDurationSemantics() {
        assertTrue(PunishmentPayloadValidation.validAction(
                new PunishmentPayloads.Action(PLAYER, "Alice", "MUTE", 60, "spam", "")));
        assertFalse(PunishmentPayloadValidation.validAction(
                new PunishmentPayloads.Action(PLAYER, "Alice", "MUTE", 0, "spam", "")));
        assertFalse(PunishmentPayloadValidation.validAction(
                new PunishmentPayloads.Action(PLAYER, "Alice", "KICK", 60, "spam", "")));
        assertTrue(PunishmentPayloadValidation.validAction(
                new PunishmentPayloads.Action("", "", "REVOKE", 0, "appeal", UUID.randomUUID().toString())));
        assertFalse(PunishmentPayloadValidation.validAction(
                new PunishmentPayloads.Action(PLAYER, "Alice", "CLEAR_RANK", 0, "", "")));
    }
}
