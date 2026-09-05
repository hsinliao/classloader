package com.hsin.sms.spi;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable-style configuration view handed to a plugin.
 *
 * <p>Values are always strings. The runtime may swap the underlying snapshot on a
 * configuration reload; providers that cache values in {@code init} must be reloaded
 * (or use {@link SmsProviderContext#config()} per invocation) to observe changes.</p>
 */
public interface PluginConfig {

    Optional<String> get(String key);

    default String getString(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    default int getInt(String key, int defaultValue) {
        Optional<String> v = get(key);
        if (v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.get().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default long getLong(String key, long defaultValue) {
        Optional<String> v = get(key);
        if (v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.get().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    Map<String, String> snapshot();
}
