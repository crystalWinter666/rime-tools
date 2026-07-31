package org.rimecraft.rimetools.module.teleport.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rimecraft.rimetools.module.teleport.model.OfflinePosition;
import org.rimecraft.rimetools.module.teleport.model.TeleportPosition;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OfflinePositionRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void missingFileLoadsAsEmpty() {
        OfflinePositionRepository repository = repository();
        repository.load();
        assertNull(repository.get(UUID.randomUUID()));
    }

    @Test
    void savesAndLoadsPositionAndPlayerName() {
        UUID playerId = UUID.randomUUID();
        OfflinePositionRepository repository = repository();
        repository.load();
        repository.set(playerId, "TestPlayer",
                new TeleportPosition("world_nether", 12.5, 64, -8.25, 90, -10), 1234);
        repository.saveIfDirty();

        OfflinePositionRepository loaded = repository();
        loaded.load();
        OfflinePosition position = loaded.get(playerId);
        assertNotNull(position);
        assertEquals("TestPlayer", position.playerName());
        assertEquals("world_nether", position.world());
        assertEquals(12.5, position.x());
        assertEquals(-8.25, position.z());
        assertEquals(1234, position.updatedAt());
        assertEquals(playerId, loaded.findPlayerId("testplayer"));
        assertEquals(playerId, loaded.findPlayerId(playerId.toString()));
    }

    @Test
    void corruptFileFallsBackToEmptyData() throws Exception {
        Files.writeString(directory.resolve("offline_positions.json"), "{not-json");
        OfflinePositionRepository repository = repository();
        repository.load();
        assertNull(repository.findPlayerId("anything"));
    }

    @Test
    void latestPositionReplacesPreviousValue() {
        UUID playerId = UUID.randomUUID();
        OfflinePositionRepository repository = repository();
        repository.set(playerId, "Player", new TeleportPosition("world", 1, 2, 3, 0, 0), 1);
        repository.set(playerId, "Renamed", new TeleportPosition("world", 4, 5, 6, 7, 8), 2);
        repository.save();

        OfflinePositionRepository loaded = repository();
        loaded.load();
        assertEquals("Renamed", loaded.get(playerId).playerName());
        assertEquals(4, loaded.get(playerId).x());
        assertNull(loaded.findPlayerId("Player"));
        assertEquals(playerId, loaded.findPlayerId("Renamed"));
    }

    @Test
    void asynchronousWriterFlushesLatestSnapshot() {
        UUID playerId = UUID.randomUUID();
        RepositoryWriter writer = new RepositoryWriter(LoggerFactory.getLogger(getClass()));
        try {
            OfflinePositionRepository repository = new OfflinePositionRepository(directory,
                    LoggerFactory.getLogger(getClass()), writer);
            repository.set(playerId, "AsyncPlayer",
                    new TeleportPosition("world", 1, 2, 3, 0, 0), 1234);
            repository.saveIfDirty();
            writer.flush();

            OfflinePositionRepository loaded = repository();
            loaded.load();
            assertEquals("AsyncPlayer", loaded.get(playerId).playerName());
        } finally {
            writer.close();
        }
    }

    @Test
    void retentionAndCountLimitsBoundOfflineData() {
        long now = System.currentTimeMillis() / 1000L;
        OfflinePositionRepository repository = new OfflinePositionRepository(directory,
                LoggerFactory.getLogger(getClass()), Runnable::run, 1, 2, 1);
        UUID old = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID newest = UUID.randomUUID();
        repository.set(old, "Old", new TeleportPosition("world", 0, 0, 0, 0, 0), now - 2 * 86400L);
        repository.set(first, "First", new TeleportPosition("world", 0, 0, 0, 0, 0), now - 10);
        repository.set(newest, "Newest", new TeleportPosition("world", 0, 0, 0, 0, 0), now);

        assertNull(repository.get(old));
        assertEquals(1, repository.knownPlayers().size());
        assertEquals(newest, repository.knownPlayers().getFirst().id());
    }

    private OfflinePositionRepository repository() {
        return new OfflinePositionRepository(directory, LoggerFactory.getLogger(getClass()));
    }
}
