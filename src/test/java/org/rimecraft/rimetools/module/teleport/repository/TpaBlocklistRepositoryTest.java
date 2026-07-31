package org.rimecraft.rimetools.module.teleport.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpaBlocklistRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void addRejectsDuplicateAndRemoveRejectsMissingEntry() {
        UUID owner = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        TpaBlocklistRepository repository = repository();

        assertTrue(repository.add(owner, blocked));
        assertFalse(repository.add(owner, blocked));
        assertTrue(repository.isBlocked(owner, blocked));
        assertTrue(repository.remove(owner, blocked));
        assertFalse(repository.remove(owner, blocked));
        assertFalse(repository.isBlocked(owner, blocked));
    }

    @Test
    void savesAndLoadsBlocklistEntries() {
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID orphaned = UUID.randomUUID();
        TpaBlocklistRepository repository = repository();
        repository.add(owner, first);
        repository.add(owner, orphaned);
        repository.saveIfDirty();

        TpaBlocklistRepository loaded = repository();
        loaded.load();
        assertTrue(loaded.isBlocked(owner, first));
        assertTrue(loaded.isBlocked(owner, orphaned));
        assertTrue(loaded.list(owner).contains(first));
        assertTrue(loaded.list(owner).contains(orphaned));
    }

    @Test
    void blocklistIsDirectional() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        TpaBlocklistRepository repository = repository();
        repository.add(owner, other);

        assertTrue(repository.isBlocked(owner, other));
        assertFalse(repository.isBlocked(other, owner));
    }

    private TpaBlocklistRepository repository() {
        return new TpaBlocklistRepository(directory, LoggerFactory.getLogger(getClass()));
    }
}
