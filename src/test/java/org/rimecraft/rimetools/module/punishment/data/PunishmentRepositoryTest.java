package org.rimecraft.rimetools.module.punishment.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    void permanentPunishmentTakesPrecedenceOverNewerTemporaryOne() {
        PunishmentRepository repository = repository();
        UUID player = UUID.randomUUID();
        repository.add(record(player, PunishmentRecord.Type.PERMA_BAN, 1000, 0));
        repository.add(record(player, PunishmentRecord.Type.TEMP_BAN, 1100, 5000));
        assertEquals(PunishmentRecord.Type.PERMA_BAN,
                repository.activeBan(player, 1200).orElseThrow().type());
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
    void revokingTemporaryPunishmentPreservesAuditRecord() {
        PunishmentRepository repository = repository();
        UUID player = UUID.randomUUID();
        repository.add(record(player, PunishmentRecord.Type.MUTE, 1000, 2000));

        assertEquals(1, repository.revoke(player, PunishmentRecord.Type.MUTE,
                1100, "Moderator", "appeal accepted"));
        assertTrue(repository.activeMute(player, 1200).isEmpty());
        PunishmentRecord audit = repository.history(player).getFirst();
        assertEquals(PunishmentRecord.Status.REVOKED, audit.status(1200));
        assertEquals("Moderator", audit.revokedBy());
        assertEquals("appeal accepted", audit.revokeReason());
    }

    @Test
    void migratesLegacyArrayToVersionedStore() throws Exception {
        UUID player = UUID.randomUUID();
        Files.writeString(directory.resolve("punishments.json"), """
                [{"playerId":"%s","playerName":"Alice","type":"PERMA_BAN",
                  "issuedAt":1000,"expiresAt":0,"reason":"legacy","executor":"Console"}]
                """.formatted(player));
        PunishmentRepository repository = repository();
        assertTrue(repository.load());
        assertTrue(repository.activeBan(player, 2000).isPresent());
        assertTrue(repository.activeBan(player, 2000).get().id() != null);
        repository.saveIfDirty();
        assertTrue(Files.readString(directory.resolve("punishments.json")).contains("\"version\": 2"));
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
    void asynchronousSaveCoalescesTheLatestSnapshot() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        PunishmentRepository repository = new PunishmentRepository(directory,
                LoggerFactory.getLogger(getClass()), queued::add);
        UUID player = UUID.randomUUID();
        repository.add(record(player, PunishmentRecord.Type.WARN, 1000, 0));
        repository.saveIfDirty();
        repository.add(record(player, PunishmentRecord.Type.KICK, 1001, 0));
        repository.saveIfDirty();

        assertEquals(1, queued.size());
        queued.getFirst().run();
        PunishmentRepository loaded = repository();
        assertTrue(loaded.load());
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
