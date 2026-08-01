package org.rimecraft.rimetools.module.chat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatConfigTest {
    @TempDir Path directory;

    @Test void usesDefaultsWhenFileIsMissing() {
        ChatConfig config = ChatConfig.load(directory.resolve("chat.yml"));
        assertEquals(true, config.antiSpamEnabled());
        assertEquals(5, config.windowSeconds());
        assertEquals(6, config.maxMessages());
        assertEquals(ChatConfig.AntiSpamAction.MUTE, config.action());
        assertEquals(60, config.muteSeconds());
        assertEquals(true, config.duplicateEnabled());
        assertEquals(true, config.blockedCommunicationCommand("/msg Alice hello"));
    }

    @Test void readsConfiguredValues() throws Exception {
        Path file = directory.resolve("chat.yml");
        Files.writeString(file, """
                anti_spam:
                  enabled: true
                  window_seconds: 3
                  max_messages: 10
                  action: KICK
                  mute_seconds: 120
                  duplicate_detection:
                    enabled: true
                    window_seconds: 30
                    max_duplicates: 3
                    similarity_threshold: 0.85
                message_rules:
                  max_length: 180
                mute_interception:
                  blocked_commands: [msg, tell, teammsg]
                """);
        ChatConfig config = ChatConfig.load(file);
        assertEquals(3, config.windowSeconds());
        assertEquals(10, config.maxMessages());
        assertEquals(ChatConfig.AntiSpamAction.KICK, config.action());
        assertEquals(120, config.muteSeconds());
        assertEquals(30, config.duplicateWindowSeconds());
        assertEquals(3, config.maxDuplicates());
        assertEquals(0.85, config.similarityThreshold());
        assertEquals(180, config.maxMessageLength());
        assertEquals(true, config.blockedCommunicationCommand("/msg Alice hello"));
        assertEquals(true, config.blockedCommunicationCommand("/minecraft:tell Alice hello"));
        assertEquals(true, config.allowedWhileMuted("/punish list"));
        assertEquals(false, config.blockedCommunicationCommand("/spawn"));
    }

    @Test void clampsUnsafeValues() throws Exception {
        Path file = directory.resolve("chat.yml");
        Files.writeString(file, """
                anti_spam:
                  window_seconds: 0
                  max_messages: -1
                  action: UNKNOWN
                  mute_seconds: 0
                  duplicate_detection:
                    similarity_threshold: 5
                message_rules:
                  max_length: 99999
                """);
        ChatConfig config = ChatConfig.load(file);
        assertEquals(1, config.windowSeconds());
        assertEquals(1, config.maxMessages());
        assertEquals(ChatConfig.AntiSpamAction.MUTE, config.action());
        assertEquals(1, config.muteSeconds());
        assertEquals(1.0, config.similarityThreshold());
        assertEquals(2048, config.maxMessageLength());
    }
}
