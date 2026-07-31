package org.rimecraft.rimetools.module.chat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatConfigTest {
    @TempDir
    Path directory;

    @Test
    void usesDefaultsWhenFileIsMissing() throws Exception {
        ChatConfig config = ChatConfig.load(directory.resolve("chat.yml"));

        assertEquals(true, config.antiSpamEnabled());
        assertEquals(5, config.windowSeconds());
        assertEquals(6, config.maxMessages());
        assertEquals(ChatConfig.AntiSpamAction.MUTE, config.action());
        assertEquals(60, config.muteSeconds());
    }

    @Test
    void readsConfiguredValues() throws Exception {
        Path file = directory.resolve("chat.yml");
        Files.writeString(file, "anti_spam:\n"
                + "  enabled: true\n"
                + "  window_seconds: 3\n"
                + "  max_messages: 10\n"
                + "  action: KICK\n"
                + "  mute_seconds: 120\n");

        ChatConfig config = ChatConfig.load(file);

        assertEquals(3, config.windowSeconds());
        assertEquals(10, config.maxMessages());
        assertEquals(ChatConfig.AntiSpamAction.KICK, config.action());
        assertEquals(120, config.muteSeconds());
    }

    @Test
    void clampsUnsafeValues() throws Exception {
        Path file = directory.resolve("chat.yml");
        Files.writeString(file, "anti_spam:\n"
                + "  window_seconds: 0\n"
                + "  max_messages: -1\n"
                + "  action: UNKNOWN\n");

        ChatConfig config = ChatConfig.load(file);

        assertEquals(1, config.windowSeconds());
        assertEquals(1, config.maxMessages());
        assertEquals(ChatConfig.AntiSpamAction.MUTE, config.action());
    }
}
