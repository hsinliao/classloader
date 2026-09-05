package com.hsin.sms.plugin;

/** Lifecycle event emitted by the plugin manager for observers/metrics/registries. */
public record PluginStateChange(
        String pluginId,
        String pluginVersion,
        PluginState previous,
        PluginState current,
        long timestampMillis) {
}
