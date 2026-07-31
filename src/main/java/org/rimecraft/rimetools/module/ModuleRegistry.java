package org.rimecraft.rimetools.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 已注册模块的注册表。所有模块统一由 {@link RimeTools} 引导注册，
 * 新模块只需实现 {@link RimeModule} 并在引导处注册，即可被统一初始化与管理。
 */
public final class ModuleRegistry {
    private final Map<String, RimeModule> modules = new LinkedHashMap<>();

    public synchronized void register(RimeModule module) {
        if (module == null || module.id() == null || module.id().isBlank()) {
            throw new IllegalArgumentException("Module and its id must not be null or blank");
        }
        RimeModule previous = modules.putIfAbsent(module.id(), module);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
    }

    public synchronized Optional<RimeModule> get(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public synchronized List<RimeModule> modules() {
        return List.copyOf(modules.values());
    }
}
