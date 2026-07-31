package org.rimecraft.rimetools.module;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Test
    void registersAndListsModules() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule a = new FakeModule("a");
        FakeModule b = new FakeModule("b");
        registry.register(a);
        registry.register(b);
        assertEquals(List.of(a, b), registry.modules());
        assertEquals(a, registry.get("a").orElseThrow());
        assertTrue(registry.get("missing").isEmpty());
    }

    @Test
    void rejectsDuplicateIds() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("a"));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new FakeModule("a")));
    }

    @Test
    void rejectsNullOrBlankIds() {
        ModuleRegistry registry = new ModuleRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(new FakeModule(null)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new FakeModule(" ")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    @Test
    void modulesReturnsImmutableSnapshot() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("a"));
        List<RimeModule> snapshot = registry.modules();
        registry.register(new FakeModule("b"));
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    private static final class FakeModule implements RimeModule {
        private final String id;

        FakeModule(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void initialize(RimeModuleContext context) {
        }
    }
}
