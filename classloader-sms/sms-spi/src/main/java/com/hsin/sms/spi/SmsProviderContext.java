package com.hsin.sms.spi;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Host services visible to a plugin during {@link SmsProvider#init}.
 *
 * <p>The interface intentionally exposes only stable JDK and SPI types. It never leaks
 * plugin-manager, plugin-runtime or classloader internals to the plugin.</p>
 */
public interface SmsProviderContext {

    String pluginId();

    String pluginVersion();

    String providerId();

    PluginConfig config();

    Optional<String> secret(String key);

    /**
     * Registers a plugin-owned resource so the runtime closes it during stop/unload.
     *
     * @return the same resource for convenience
     */
    <T extends AutoCloseable> T registerResource(String name, T resource);

    /**
     * Creates a thread factory whose threads are named, daemon, tracked by the runtime,
     * and run with the plugin class loader as their context class loader.
     */
    ThreadFactory newThreadFactory(String namePrefix);

    /** Creates a tracked, daemon executor that the runtime shuts down during stop/unload. */
    ExecutorService newExecutor(String name, int poolSize);

    /** Creates a tracked, daemon scheduled executor owned by the plugin lifecycle. */
    ScheduledExecutorService newScheduledExecutor(String name, int poolSize);
}
