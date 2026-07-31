package org.rimecraft.rimetools.module.punishment.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentRepositoryTest {
    @TempDir
    Path directory;

    private static PunishmentRecord record(UUID player, PunishmentRecord.Type type, long issuedAt, long expiresAt) {
        return new PunishmentRecord(player, "Alice", type, issuedAt, expiresAt, "test", "Console");
    }

    @Test
    void activeBanAndMuteFollowExpiry() {
        PunishmentRepository repository = repository();
        UUID player = UUID.randomUUID();
        long now = 1000;

        repository.add(record(player, PunishmentRecord.Type.TEMP_BAN, now, now + 60));
        repository.add(record(player, PunishmentRecord.Type.MUTE, now, now + 30));
        assertTrue(repository.activeBan(player, now + 10).isPresent());
        assertTrue(repository.activeMute(player, now + 10).isPresent());
        assertTrue(repository.activeBan(player, now + 61).isEmpty());
        assertTrue(repository.activeMute(player, now + 31).isEmpty());
    }

    @Test
    void permanentBanNeverExpires() {
        PunishmentRepository repository = repository();
        UUID player = UUID.randomUUID();
        repository.add(record(player, PunishmentRecord.Type.PERMA_BAN, 1000, 0));
        assertTrue(repository.activeBan(player, Long.MAX_VALUE).isPresent());
    }

    @Test
    void revokeClearsActivePunishment() {
        PunishmentRepository repository = repository();
        UUID player = UUID.randomUUID();
        repository.add(record(player, PunishmentRecord.Type.PERMA_BAN, 1000, 0));
        assertTrue(repository.revoke(player, PunishmentRecord.Type.PERMA_BAN));
        assertTrue(repository.activeBan(player, 2000).isEmpty());
        assertFalse(repository.revoke(player, PunishmentRecord.Type.PERMA_BAN));
    }

    @Test
    void savesAndLoadsRecords() {
        PunishmentRepository repository = repository();
        UUID player = UUID.randomUUID();
        long now = 1000;
        repository.add(record(player, PunishmentRecord.Type.TEMP_BAN, now, now + 60));
        repository.add(record(player, PunishmentRecord.Type.KICK, now, 0));
        repository.saveIfDirty();

        PunishmentRepository loaded = repository();
        loaded.load();
        assertTrue(loaded.activeBan(player, now + 10).isPresent());
        assertEquals(2, loaded.history(player).size());
    }

    @Test
    void pruneExpiredRemovesOldActiveRecords() {
        PunishmentRepository repository = repository();
        UUID player = UUID.randomUUID();
        long now = 1000;
        repository.add(record(player, PunishmentRecord.Type.MUTE, now, now + 10));
        repository.add(record(player, PunishmentRecord.Type.PERMA_BAN, now, 0));

        repository.pruneExpired(now + 20);
        assertTrue(repository.activeMute(player, now + 20).isEmpty());
        assertTrue(repository.activeBan(player, now + 20).isPresent());
    }

    private PunishmentRepository repository() {
        return new PunishmentRepository(directory, LoggerFactory.getLogger(getClass()));
    }
}
