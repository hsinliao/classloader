package com.hsin.sms.service;

import com.hsin.sms.plugin.PluginGateway;
import com.hsin.sms.plugin.PluginLifecycleListener;
import com.hsin.sms.plugin.PluginSnapshot;
import com.hsin.sms.plugin.PluginState;
import com.hsin.sms.plugin.PluginStateChange;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business-side directory of currently RUNNING providers. It mirrors lifecycle
 * events from the runtime and never holds class loaders or plugin internals.
 */
public final class ProviderRegistry implements PluginLifecycleListener {

    private final Set<String> runningProviders = ConcurrentHashMap.newKeySet();

    public ProviderRegistry(PluginGateway gateway) {
        refresh(gateway);
    }

    /** Rebuilds the running-provider set from the current gateway state. */
    public void refresh(PluginGateway gateway) {
        for (PluginSnapshot snapshot : gateway.getPlugins()) {
            if (snapshot.state() == PluginState.RUNNING) {
                runningProviders.add(snapshot.pluginId());
            } else {
                runningProviders.remove(snapshot.pluginId());
            }
        }
    }

    /** Incremental update driven by manager lifecycle events. */
    @Override
    public void onStateChanged(PluginStateChange change) {
        if (change.current() == PluginState.RUNNING) {
            runningProviders.add(change.pluginId());
        } else {
            runningProviders.remove(change.pluginId());
        }
    }

    /** Whether the provider id is currently RUNNING. */
    public boolean isRunning(String providerId) {
        return runningProviders.contains(providerId);
    }

    /** Snapshot of running provider ids. */
    public Set<String> runningProviders() {
        return Set.copyOf(runningProviders);
    }
}
