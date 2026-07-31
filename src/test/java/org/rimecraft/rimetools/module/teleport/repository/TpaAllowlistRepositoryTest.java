package org.rimecraft.rimetools.module.teleport.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpaAllowlistRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void addRejectsDuplicateAndRemoveRejectsMissingEntry() {
        UUID owner = UUID.randomUUID();
        UUID allowed = UUID.randomUUID();
        TpaAllowlistRepository repository = repository();

        assertTrue(repository.add(owner, allowed));
        assertFalse(repository.add(owner, allowed));
        assertTrue(repository.isAllowed(owner, allowed));
        assertTrue(repository.remove(owner, allowed));
        assertFalse(repository.remove(owner, allowed));
        assertFalse(repository.isAllowed(owner, allowed));
    }

    @Test
    void savesAndLoadsAllowlistEntries() {
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID orphaned = UUID.randomUUID();
        TpaAllowlistRepository repository = repository();
        repository.add(owner, first);
        repository.add(owner, orphaned);
        repository.saveIfDirty();

        TpaAllowlistRepository loaded = repository();
        loaded.load();
        assertTrue(loaded.isAllowed(owner, first));
        assertTrue(loaded.isAllowed(owner, orphaned));
        assertTrue(loaded.list(owner).contains(first));
        assertTrue(loaded.list(owner).contains(orphaned));
    }

    private TpaAllowlistRepository repository() {
        return new TpaAllowlistRepository(directory, LoggerFactory.getLogger(getClass()));
    }
}
