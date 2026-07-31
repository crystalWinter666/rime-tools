package org.rimecraft.rimetools.client.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 已注册客户端模块的注册表，与服务端 {@code ModuleRegistry} 对称。
 * 新模块只需实现 {@link RimeClientModule} 并在 {@code RimeToolsClient} 注册，
 * 即可被统一初始化。
 */
public final class ClientModuleRegistry {
    private final Map<String, RimeClientModule> modules = new LinkedHashMap<>();

    public synchronized void register(RimeClientModule module) {
        if (module == null || module.id() == null || module.id().isBlank()) {
            throw new IllegalArgumentException("Module and its id must not be null or blank");
        }
        RimeClientModule previous = modules.putIfAbsent(module.id(), module);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
    }

    public synchronized Optional<RimeClientModule> get(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public synchronized List<RimeClientModule> modules() {
        return List.copyOf(modules.values());
    }
}
