package com.hsin.sms.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry of loaded {@link PluginRuntime}s, keyed by unique plugin id.
 * Lifecycle mutations are serialized by the plugin manager.
 */
public final class PluginRegistry {

    private final ConcurrentMap<String, PluginRuntime> runtimes = new ConcurrentHashMap<>();

    PluginRuntime get(String pluginId) {
        return runtimes.get(pluginId);
    }

    boolean contains(String pluginId) {
        return runtimes.containsKey(pluginId);
    }

    PluginRuntime put(String pluginId, PluginRuntime runtime) {
        return runtimes.put(pluginId, runtime);
    }

    PluginRuntime remove(String pluginId) {
        return runtimes.remove(pluginId);
    }

    List<PluginRuntime> all() {
        return new ArrayList<>(runtimes.values());
    }

    boolean isEmpty() {
        return runtimes.isEmpty();
    }

    int size() {
        return runtimes.size();
    }
}
