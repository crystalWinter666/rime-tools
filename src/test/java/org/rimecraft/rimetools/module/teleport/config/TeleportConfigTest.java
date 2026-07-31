package org.rimecraft.rimetools.module.teleport.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportConfigTest {
    @TempDir
    Path directory;

    @Test
    void clampsUnsafeConfirmationTimeout() throws Exception {
        Files.writeString(directory.resolve("teleport.yml"), "confirm_timeout_seconds: 0\n");

        TeleportConfig config = TeleportConfig.load(directory.resolve("teleport.yml"));

        assertEquals(5, config.confirmTimeoutSeconds);
    }

    @Test
    void readsAntiSpamSettingsWithClamping() throws Exception {
        Files.writeString(directory.resolve("teleport.yml"),
                "tpa_request_cooldown_seconds: -1\n"
                        + "tpa_target_chat_limit: -5\n"
                        + "tpa_target_chat_window_seconds: 0\n");

        TeleportConfig config = TeleportConfig.load(directory.resolve("teleport.yml"));

        assertEquals(0, config.tpaRequestCooldownSeconds);
        assertEquals(0, config.tpaTargetChatLimit);
        assertEquals(1, config.tpaTargetChatWindowSeconds);
    }

    @Test
    void usesDefaultAntiSpamSettings() throws Exception {
        TeleportConfig config = TeleportConfig.load(directory.resolve("teleport.yml"));

        assertEquals(5, config.tpaRequestCooldownSeconds);
        assertEquals(5, config.tpaTargetChatLimit);
        assertEquals(10, config.tpaTargetChatWindowSeconds);
    }
}
