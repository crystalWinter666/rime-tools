package org.rimecraft.rimetools.module.title.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadValidationTest {
    @Test
    void validatesPlayerNamesWithoutAcceptingCommandsOrUuids() {
        assertTrue(PayloadValidation.isValidPlayerTarget("Alice_01"));
        assertTrue(PayloadValidation.isValidPlayerTarget("123e4567-e89b-12d3-a456-426614174000"));
        assertFalse(PayloadValidation.isValidPlayerTarget("/op Alice"));
        assertFalse(PayloadValidation.isValidPlayerTarget("a".repeat(17)));
    }

    @Test
    void validatesCompleteTitlePayloads() {
        assertTrue(PayloadValidation.isValidTitleInput("vip", "VIP", "#55FFFF", 10));
        assertFalse(PayloadValidation.isValidTitleInput("VIP", "VIP", "#55FFFF", 10));
        assertFalse(PayloadValidation.isValidTitleInput("vip", "", "#55FFFF", 10));
        assertFalse(PayloadValidation.isValidTitleInput("vip", "VIP", "red", 10));
        assertFalse(PayloadValidation.isValidTitleInput("vip", "VIP", "#55FFFF", 100_001));
    }
}
