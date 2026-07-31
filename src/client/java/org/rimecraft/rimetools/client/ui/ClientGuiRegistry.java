package org.rimecraft.rimetools.client.ui;

import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端 GUI 入口注册表。各模块在客户端初始化阶段注册自己的界面入口，
 * {@link ModuleSwitcher} 通过本表渲染并跳转；新增模块只需调用
 * {@link #register(String, Component, Runnable)}，无需修改本类。
 */
public final class ClientGuiRegistry {
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private ClientGuiRegistry() {
    }

    public static synchronized void register(String id, Component label, Runnable opener) {
        if (id == null || id.isBlank() || label == null || opener == null) {
            throw new IllegalArgumentException("id, label and opener must not be null");
        }
        if (ENTRIES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate GUI entry id: " + id);
        }
        ENTRIES.put(id, new Entry(id, label, opener));
    }

    public static synchronized List<Entry> entries() {
        return List.copyOf(ENTRIES.values());
    }

    public static synchronized void open(String id) {
        Entry entry = ENTRIES.get(id);
        if (entry != null) {
            entry.opener().run();
        }
    }

    public record Entry(String id, Component label, Runnable opener) {
    }
}
