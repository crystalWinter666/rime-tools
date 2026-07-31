package org.rimecraft.rimetools.module.teleport.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rimecraft.rimetools.module.teleport.model.Waypoint;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaypointRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void reloadsWaypointFilesCopiedIntoARunningServer() throws Exception {
        UUID owner = UUID.randomUUID();
        WaypointRepository repository = repository();
        assertTrue(repository.load());

        String personalJson = """
                {
                  "%s": {
                    "home": {
                      "name": "home", "world": "world", "x": 1.5, "y": 64.0, "z": -2.5,
                      "yaw": 90.0, "pitch": 0.0, "description": "legacy personal",
                      "owner": "%s", "createdAt": 1, "updatedAt": 2
                    }
                  }
                }
                """.formatted(owner, owner);
        String globalJson = """
                {
                  "spawn": {
                    "name": "spawn", "world": "world", "x": 0.0, "y": 80.0, "z": 0.0,
                    "yaw": 0.0, "pitch": 0.0, "description": "legacy global",
                    "owner": "%s", "createdAt": 3, "updatedAt": 4
                  }
                }
                """.formatted(owner);
        Files.writeString(directory.resolve("personal_waypoints.json"), personalJson, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("global_waypoints.json"), globalJson, StandardCharsets.UTF_8);

        assertTrue(repository.load());
        assertEquals(1, repository.countPersonal());
        assertEquals(1, repository.countGlobal());
        assertEquals("legacy personal", repository.getPersonal(owner, "home").getDescription());
        assertNull(repository.getPersonal(owner, "home").getAlias());
        assertEquals("legacy global", repository.getGlobal("spawn").getDescription());
    }

    @Test
    void cleanShutdownDoesNotOverwriteExternallyCopiedFiles() throws Exception {
        WaypointRepository repository = repository();
        assertTrue(repository.load());
        String imported = "{\"spawn\":{\"name\":\"spawn\",\"world\":\"world\",\"x\":0," +
                "\"y\":64,\"z\":0,\"yaw\":0,\"pitch\":0,\"description\":\"imported\"," +
                "\"createdAt\":1,\"updatedAt\":1}}";
        Path globalFile = directory.resolve("global_waypoints.json");
        Files.writeString(globalFile, imported, StandardCharsets.UTF_8);

        repository.saveIfDirty();

        assertEquals(imported, Files.readString(globalFile, StandardCharsets.UTF_8));
    }

    @Test
    void malformedReloadKeepsCurrentInMemoryWaypoints() throws Exception {
        UUID owner = UUID.randomUUID();
        WaypointRepository repository = repository();
        repository.setPersonal(owner, new Waypoint("home", "world", 1, 64, 2,
                0, 0, "current", owner, 1, 1));
        Files.writeString(directory.resolve("personal_waypoints.json"), "{broken", StandardCharsets.UTF_8);

        assertFalse(repository.load());
        assertEquals("current", repository.getPersonal(owner, "home").getDescription());
    }

    private WaypointRepository repository() {
        return new WaypointRepository(directory, LoggerFactory.getLogger(getClass()));
    }
}
