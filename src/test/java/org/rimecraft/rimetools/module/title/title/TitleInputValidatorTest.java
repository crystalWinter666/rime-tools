package org.rimecraft.rimetools.module.title.title;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TitleInputValidatorTest {
    @Test
    void acceptsSafeTitleInput() {
        assertTrue(TitleInputValidator.isValidId("mvp_plus"));
        assertTrue(TitleInputValidator.isValidDisplayName("MVP+"));
        assertTrue(TitleInputValidator.isValidColor("#55FFFF"));
    }

    @Test
    void rejectsUnsafeTitleInput() {
        assertFalse(TitleInputValidator.isValidId("MVP plus"));
        assertFalse(TitleInputValidator.isValidId("../config"));
        assertFalse(TitleInputValidator.isValidDisplayName("bad\nname"));
        assertFalse(TitleInputValidator.isValidColor("red"));
    }

    @Test
    void normalizesColorToUppercaseHex() {
        assertEquals("#55FFFF", TitleInputValidator.normalizeColor("55ffff").orElseThrow());
    }

    @Test
    void rejectsOverlongAndControlCharacterDisplayNames() {
        assertFalse(TitleInputValidator.isValidDisplayName("x".repeat(33)));
        assertFalse(TitleInputValidator.isValidDisplayName("bad\u0000name"));
    }

    @Test
    void titleDefinitionNormalizesColorAndChecksWeight() {
        TitleDefinition title = new TitleDefinition("vip", "VIP", "55ffff", 10, true);
        assertEquals("#55FFFF", title.color());
        assertThrows(IllegalArgumentException.class,
                () -> new TitleDefinition("vip", "VIP", "#FFFFFF", 100_001, true));
    }
}
