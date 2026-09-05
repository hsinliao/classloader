package com.hsin.sms.plugin;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread factory that names threads, marks them daemon, installs the plugin class
 * loader as TCCL, and reports uncaught failures to the plugin metrics.
 */
public final class PluginThreadFactory implements ThreadFactory {

    private static final Logger LOG = Logger.getLogger("com.hsin.sms.plugin.runtime");

    private final String pluginId;
    private final String prefix;
    private final ClassLoader pluginClassLoader;
    private final PluginMetrics metrics;
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicLong createdThreads = new AtomicLong();

    public PluginThreadFactory(String pluginId, String prefix,
                               ClassLoader pluginClassLoader, PluginMetrics metrics) {
        this.pluginId = pluginId;
        this.prefix = prefix;
        this.pluginClassLoader = pluginClassLoader;
        this.metrics = metrics;
    }

    /** Creates a named, daemon thread with the plugin class loader as TCCL. */
    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, prefix + "-" + sequence.incrementAndGet());
        thread.setDaemon(true);
        thread.setContextClassLoader(pluginClassLoader);
        thread.setUncaughtExceptionHandler((t, e) -> {
            metrics.recordUnexpectedThreadFailure(e);
            LOG.log(Level.SEVERE, "uncaught exception in plugin thread " + t.getName(), e);
        });
        createdThreads.incrementAndGet();
        return thread;
    }

    /** Number of threads created through this factory (diagnostics). */
    public long createdThreadCount() {
        return createdThreads.get();
    }

    /** Owning plugin id. */
    public String pluginId() {
        return pluginId;
    }
}
