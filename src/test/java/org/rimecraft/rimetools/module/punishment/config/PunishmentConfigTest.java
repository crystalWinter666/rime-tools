package org.rimecraft.rimetools.module.punishment.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentConfigTest {
    @TempDir
    Path directory;

    @Test
    void usesDefaultWhenFileIsMissing() throws Exception {
        assertEquals(true, PunishmentConfig.load(directory.resolve("punishment.yml")).announcePunishments());
    }

    @Test
    void readsConfiguredValue() throws Exception {
        Path file = directory.resolve("punishment.yml");
        Files.writeString(file, "announce_punishments: false\n"
                + "history_page_size: 500\n"
                + "mute_notice_cooldown_seconds: -2\n");

        PunishmentConfig config = PunishmentConfig.load(file);
        assertEquals(false, config.announcePunishments());
        assertEquals(50, config.historyPageSize());
        assertEquals(0, config.muteNoticeCooldownSeconds());
    }
}
