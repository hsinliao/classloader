package com.hsin.sms.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns plugin-registered resources and closes them in reverse registration order.
 *
 * <p>Close is idempotent; every close failure is logged and aggregated so one broken
 * resource cannot hide the others.</p>
 */
public final class PluginResourceRegistry {

    private static final Logger LOG = Logger.getLogger("com.hsin.sms.plugin.runtime.resources");

    private record Entry(String name, AutoCloseable resource, long order) {
    }

    private final Map<String, Entry> resources = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean closed;

    /**
     * Registers a plugin-owned resource. Re-registering the same name replaces the
     * previous entry (the old entry is intentionally not closed automatically).
     */
    public <T extends AutoCloseable> T register(String name, T resource) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("resource name must not be blank");
        }
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        Entry previous = resources.put(name, new Entry(name, resource, sequence.incrementAndGet()));
        if (previous != null) {
            LOG.warning("resource '" + name + "' was registered twice; previous entry replaced");
        }
        return resource;
    }

    /** Names in registration order (diagnostics). */
    public List<String> names() {
        return resources.values().stream().sorted((a, b) -> Long.compare(a.order, b.order))
                .map(Entry::name).toList();
    }

    /** Number of currently registered resources. */
    public int size() {
        return resources.size();
    }

    /** True after {@link #closeAll()} has been called at least once. */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Closes resources in reverse registration order and aggregates failures so one
     * broken resource cannot prevent the others from being released. Idempotent.
     */
    public List<Throwable> closeAll() {
        List<Throwable> failures = new ArrayList<>();
        closed = true;
        List<Entry> ordered = resources.values().stream()
                .sorted((a, b) -> Long.compare(b.order, a.order))
                .toList();
        for (Entry entry : ordered) {
            try {
                entry.resource().close();
            } catch (Throwable t) {
                failures.add(t);
                LOG.log(Level.SEVERE, "failed to close plugin resource '" + entry.name() + "'", t);
            }
        }
        resources.clear();
        return failures;
    }
}
