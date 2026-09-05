package com.hsin.sms.plugin;

import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Host-side plugin manager.
 *
 * <p>The manager owns the registry and serializes lifecycle mutations
 * (load/start/stop/unload/reload/upgrade/rollback). Sends are lock-free lookups plus
 * per-runtime bulkhead/drain control, so sends and lifecycle operations cooperate
 * correctly without a global lock on the hot path.</p>
 */
public final class PluginManager implements PluginGateway, AutoCloseable {

    private static final Logger LOG = Logger.getLogger("com.hsin.sms.plugin.runtime.manager");

    private final PluginRuntimeSettings settings;
    private final PluginRegistry registry = new PluginRegistry();
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final PluginDescriptorParser descriptorParser = new PluginDescriptorParser();
    private final CopyOnWriteArrayList<PluginLifecycleListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Path> previousDirectories = new ConcurrentHashMap<>();

    private volatile PluginConfigSource configSource = new PropertiesConfigSource();
    private volatile SecretProvider secretProvider = new EnvironmentSecretProvider();
    private volatile PluginIntegrityPolicy integrityPolicy = PluginIntegrityPolicy.ALWAYS_TRUST;
    private volatile boolean closed;

    /** Creates a manager with {@link PluginRuntimeSettings#defaults()}. */
    public PluginManager() {
        this(PluginRuntimeSettings.defaults());
    }

    /** Creates a manager with explicit runtime tuning knobs. */
    public PluginManager(PluginRuntimeSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Overrides how {@code config/} files are read (default: properties files). */
    public PluginManager configSource(PluginConfigSource configSource) {
        this.configSource = Objects.requireNonNull(configSource, "configSource");
        return this;
    }

    /** Overrides how plugin secrets are resolved. */
    public PluginManager secretProvider(SecretProvider secretProvider) {
        this.secretProvider = Objects.requireNonNull(secretProvider, "secretProvider");
        return this;
    }

    /** Overrides artifact integrity verification. */
    public PluginManager integrityPolicy(PluginIntegrityPolicy integrityPolicy) {
        this.integrityPolicy = Objects.requireNonNull(integrityPolicy, "integrityPolicy");
        return this;
    }

    /** Registers a listener for coarse lifecycle events (RUNNING/STOPPED/etc.). */
    public PluginManager addLifecycleListener(PluginLifecycleListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    /** Removes a previously registered lifecycle listener. */
    public PluginManager removeLifecycleListener(PluginLifecycleListener listener) {
        listeners.remove(listener);
        return this;
    }

    // ------------------------------------------------------------------ load

    /**
     * Parses, validates and prepares a plugin directory.
     *
     * @return the plugin in {@link PluginState#LOADED} state (not yet RUNNING)
     * @throws PluginDescriptorException on invalid metadata
     * @throws PluginCompatibilityException on SPI/JVM incompatibility
     * @throws PluginLoadException on discovery/integrity failures
     */
    public PluginSnapshot loadPlugin(Path pluginDirectory) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            Path dir = pluginDirectory.toAbsolutePath().normalize();
            PluginDescriptor descriptor = descriptorParser.parse(dir);
            if (registry.contains(descriptor.id())) {
                throw new PluginLoadException(descriptor.id(), descriptor.version(),
                        "plugin id '" + descriptor.id() + "' is already loaded", null);
            }
            PluginRuntime runtime = createRuntime(descriptor);
            try {
                runtime.load();
            } catch (Throwable t) {
                disposeAfterFailedLoad(runtime);
                throw t;
            }
            if (runtime.state() != PluginState.LOADED) {
                disposeAfterFailedLoad(runtime);
                throw new PluginLoadException(descriptor.id(), descriptor.version(),
                        "plugin did not reach LOADED state (state=" + runtime.state() + ")", null);
            }
            registry.put(descriptor.id(), runtime);
            publish(descriptor.id(), descriptor.version(), PluginState.DISCOVERED, PluginState.LOADED);
            return runtime.snapshot();
        } finally {
            lifecycleLock.unlock();
        }
    }

    private PluginRuntime createRuntime(PluginDescriptor descriptor) {
        return new PluginRuntime(descriptor, configSource, secretProvider, integrityPolicy, settings);
    }

    private void disposeAfterFailedLoad(PluginRuntime runtime) {
        try {
            runtime.unload();
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "failed to dispose plugin after load failure", t);
        }
    }

    // ----------------------------------------------------------------- start

    /**
     * Creates the provider and marks the plugin RUNNING. Restarting a previously
     * stopped (but still loaded) plugin is supported.
     */
    public PluginSnapshot startPlugin(String pluginId) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            PluginRuntime runtime = requireRuntime(pluginId);
            PluginState previous = runtime.state();
            try {
                startPluginInternal(runtime);
            } catch (Throwable t) {
                registry.remove(pluginId);
                throw t;
            }
            publish(pluginId, runtime.descriptor().version(), previous, runtime.state());
            return runtime.snapshot();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** Stops the plugin using the default drain/shutdown timeouts. */
    public PluginSnapshot stopPlugin(String pluginId) {
        return stopPlugin(pluginId, settings.drainTimeout(), settings.shutdownTimeout());
    }

    /**
     * Gracefully stops a plugin: DRAINING rejects new requests, waits for in-flight
     * calls up to {@code drainTimeout}, then shuts down the provider/executors.
     */
    public PluginSnapshot stopPlugin(String pluginId, Duration drainTimeout, Duration shutdownTimeout) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            PluginRuntime runtime = requireRuntime(pluginId);
            PluginState previous = runtime.state();
            runtime.stop(drainTimeout, shutdownTimeout);
            publish(pluginId, runtime.descriptor().version(), previous, runtime.state());
            return runtime.snapshot();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** Explicit drain entry point, primarily useful for tooling/monitoring. */
    public PluginSnapshot drainPlugin(String pluginId, Duration timeout) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            PluginRuntime runtime = requireRuntime(pluginId);
            PluginState previous = runtime.state();
            runtime.stop(timeout, settings.shutdownTimeout());
            publish(pluginId, runtime.descriptor().version(), previous, runtime.state());
            return runtime.snapshot();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Fully releases the plugin: provider, resources, worker threads and the
     * class loader, then removes it from the registry.
     */
    public PluginSnapshot unloadPlugin(String pluginId) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            PluginRuntime runtime = requireRuntime(pluginId);
            PluginState previous = runtime.state();
            runtime.unload();
            registry.remove(pluginId);
            publish(pluginId, runtime.descriptor().version(), previous, PluginState.UNLOADED);
            return runtime.snapshot();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Rebuilds the plugin from its current directory. If the reload fails, the same
     * directory is automatically reloaded as a rollback.
     */
    public PluginSnapshot reloadPlugin(String pluginId) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            PluginRuntime old = requireRuntime(pluginId);
            Path dir = old.descriptor().pluginDir();
            PluginState previous = old.state();
            unloadInternal(old);
            registry.remove(pluginId);
            try {
                PluginRuntime replacement = createRuntime(descriptorParser.parse(dir));
                replacement.load();
                registry.put(pluginId, replacement);
                startPluginInternal(replacement);
                publish(pluginId, replacement.descriptor().version(), previous, PluginState.RUNNING);
                return replacement.snapshot();
            } catch (Throwable t) {
                try {
                    PluginRuntime restored = createRuntime(descriptorParser.parse(dir));
                    restored.load();
                    registry.put(pluginId, restored);
                    startPluginInternal(restored);
                    LOG.log(Level.WARNING,
                            "reload failed; rolled plugin '" + pluginId + "' back from same directory", t);
                    return restored.snapshot();
                } catch (Throwable rollbackError) {
                    throw new PluginLoadException(pluginId, null,
                            "reload failed and rollback also failed: "
                                    + PluginRuntime.rootMessage(rollbackError), rollbackError);
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    // ------------------------------------------------------------- upgrade

    /**
     * Loads and validates the new directory while the old version keeps serving,
     * then atomically swaps the registry entry and starts the new version.
     * On start failure the previous directory is reloaded automatically.
     */
    public PluginSnapshot upgradePlugin(String pluginId, Path newPluginDirectory) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            PluginRuntime old = requireRuntime(pluginId);
            Path oldDir = old.descriptor().pluginDir();
            PluginDescriptor newDescriptor = descriptorParser.parse(newPluginDirectory);
            if (!newDescriptor.id().equals(pluginId)) {
                throw new PluginLoadException(newDescriptor.id(), newDescriptor.version(),
                        "upgrade directory is for plugin '" + newDescriptor.id()
                                + "' but requested plugin is '" + pluginId + "'", null);
            }
            PluginRuntime candidate = createRuntime(newDescriptor);
            try {
                candidate.load();
            } catch (Throwable t) {
                disposeAfterFailedLoad(candidate);
                throw t;
            }
            try {
                PluginState oldPrevious = old.state();
                unloadInternal(old);
                registry.remove(pluginId);
                registry.put(pluginId, candidate);
                startPluginInternal(candidate);
                previousDirectories.put(pluginId, oldDir);
                publish(pluginId, candidate.descriptor().version(), oldPrevious, PluginState.RUNNING);
                return candidate.snapshot();
            } catch (Throwable t) {
                disposeAfterFailedLoad(candidate);
                registry.remove(pluginId);
                // automatic rollback to the previous directory
                try {
                    PluginRuntime restored = createRuntime(descriptorParser.parse(oldDir));
                    restored.load();
                    registry.put(pluginId, restored);
                    startPluginInternal(restored);
                    LOG.log(Level.WARNING, "upgrade failed; rolled back plugin '" + pluginId
                            + "' to " + oldDir, t);
                    return restored.snapshot();
                } catch (Throwable rollbackError) {
                    throw new PluginLoadException(pluginId, null,
                            "upgrade failed and rollback also failed: "
                                    + PluginRuntime.rootMessage(rollbackError), rollbackError);
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** Rolls back to the directory that ran before the last successful upgrade. */
    /**
     * Switches to the directory that ran before the last successful upgrade.
     * Rolling back again returns to the newer directory.
     */
    public PluginSnapshot rollbackPlugin(String pluginId) {
        lifecycleLock.lock();
        try {
            ensureOpen();
            PluginRuntime current = requireRuntime(pluginId);
            Path targetDir = previousDirectories.get(pluginId);
            if (targetDir == null) {
                throw new PluginStateException(pluginId, current.descriptor().version(),
                        "no previous version recorded for rollback");
            }
            Path currentDir = current.descriptor().pluginDir();
            PluginState previous = current.state();
            unloadInternal(current);
            registry.remove(pluginId);
            try {
                PluginRuntime restored = createRuntime(descriptorParser.parse(targetDir));
                restored.load();
                registry.put(pluginId, restored);
                startPluginInternal(restored);
                previousDirectories.put(pluginId, currentDir);
                publish(pluginId, restored.descriptor().version(), previous, PluginState.RUNNING);
                return restored.snapshot();
            } catch (Throwable t) {
                throw new PluginLoadException(pluginId, null,
                        "rollback of plugin '" + pluginId + "' failed: "
                                + PluginRuntime.rootMessage(t), t);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    // ------------------------------------------------------------- queries

    @Override
    /** Sends through a loaded plugin; bulkhead/timeout/capability policy is runtime-owned. */
    public SmsResponse send(String pluginId, SmsRequest request) {
        ensureOpen();
        PluginRuntime runtime = registry.get(pluginId);
        if (runtime == null) {
            throw new PluginNotFoundException(pluginId);
        }
        return runtime.send(request);
    }

    @Override
    /** Returns an immutable snapshot, or {@code null} when the plugin is not loaded. */
    public PluginSnapshot getPlugin(String pluginId) {
        PluginRuntime runtime = registry.get(pluginId);
        return runtime == null ? null : runtime.snapshot();
    }

    @Override
    /** Returns sorted snapshots of every loaded plugin runtime. */
    public List<PluginSnapshot> getPlugins() {
        List<PluginSnapshot> result = new ArrayList<>();
        for (PluginRuntime runtime : registry.all()) {
            result.add(runtime.snapshot());
        }
        result.sort((a, b) -> a.pluginId().compareTo(b.pluginId()));
        return List.copyOf(result);
    }

    /** Whether the registry currently contains the plugin id. */
    public boolean isLoaded(String pluginId) {
        return registry.contains(pluginId);
    }

    /** Host-side diagnostics access to a plugin class loader. */
    /** Host-side diagnostics access to the plugin class loader. */
    public ClassLoader pluginClassLoader(String pluginId) {
        return requireRuntime(pluginId).pluginClassLoader();
    }

    /** Re-reads the plugin {@code config/} directory and atomically swaps the snapshot. */
    public PluginSnapshot reloadConfig(String pluginId) {
        lifecycleLock.lock();
        try {
            PluginRuntime runtime = requireRuntime(pluginId);
            Path dir = runtime.descriptor().pluginDir();
            runtime.configView().replace(configSource.load(dir));
            return runtime.snapshot();
        } finally {
            lifecycleLock.unlock();
        }
    }

    // --------------------------------------------------------------- close

    /**
     * Stops and unloads every plugin. Idempotent and safe to call repeatedly.
     * Further lifecycle/send calls are rejected after close.
     */
    @Override
    public void close() {
        lifecycleLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            for (PluginRuntime runtime : registry.all()) {
                try {
                    runtime.unload();
                } catch (Throwable t) {
                    LOG.log(Level.WARNING, "failed to unload '" + runtime.descriptor().id()
                            + "' during manager shutdown", t);
                }
            }
            previousDirectories.clear();
        } finally {
            lifecycleLock.unlock();
        }
    }

    // ------------------------------------------------------------- helpers

    private void startPluginInternal(PluginRuntime runtime) {
        try {
            runtime.start();
            if (runtime.state() != PluginState.RUNNING) {
                throw new PluginStateException(runtime.descriptor().id(),
                        runtime.descriptor().version(), "plugin did not reach RUNNING");
            }
        } catch (Throwable t) {
            disposeAfterFailedLoad(runtime);
            throw t;
        }
    }

    private void unloadInternal(PluginRuntime runtime) {
        runtime.unload();
    }

    private PluginRuntime requireRuntime(String pluginId) {
        PluginRuntime runtime = registry.get(pluginId);
        if (runtime == null) {
            throw new PluginNotFoundException(pluginId);
        }
        return runtime;
    }

    private void publish(String pluginId, String version, PluginState previous, PluginState current) {
        if (previous == current) {
            return;
        }
        PluginStateChange change = new PluginStateChange(pluginId, version, previous, current,
                System.currentTimeMillis());
        for (PluginLifecycleListener listener : listeners) {
            try {
                listener.onStateChanged(change);
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "lifecycle listener failed", t);
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new PluginStateException(null, null, "plugin manager is closed");
        }
    }
}
