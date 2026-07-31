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
}
