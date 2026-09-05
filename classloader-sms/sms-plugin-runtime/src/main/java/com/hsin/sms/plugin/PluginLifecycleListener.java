package com.hsin.sms.plugin;

/** Observer for lifecycle events; must be fast and must not throw. */
@FunctionalInterface
public interface PluginLifecycleListener {

    void onStateChanged(PluginStateChange change);
}
