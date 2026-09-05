package com.hsin.sms.plugin;

import com.hsin.sms.spi.PluginConfig;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Volatile immutable config snapshot handed to plugins. Reload replaces the whole map.
 */
public final class PluginConfigView implements PluginConfig {

    private final AtomicReference<Map<String, String>> snapshot = new AtomicReference<>(Map.of());

    /** Creates a view backed by an immutable copy of {@code initial}. */
    public PluginConfigView(Map<String, String> initial) {
        snapshot.set(Map.copyOf(initial));
    }

    /** Atomically replaces the whole configuration snapshot (config reload). */
    public void replace(Map<String, String> next) {
        snapshot.set(Map.copyOf(next));
    }

    /** Returns the value for {@code key}, if present in the current snapshot. */
    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(snapshot.get().get(key));
    }

    /** Immutable view of the current configuration. */
    @Override
    public Map<String, String> snapshot() {
        return snapshot.get();
    }
}
