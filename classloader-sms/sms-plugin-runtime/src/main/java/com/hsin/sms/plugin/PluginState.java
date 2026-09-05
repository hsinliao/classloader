package com.hsin.sms.plugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Formal plugin lifecycle state machine.
 *
 * <p>Unloading never happens directly from RUNNING: the machine forces
 * {@code RUNNING -> DRAINING -> STOPPING -> STOPPED -> UNLOADED}.</p>
 */
public enum PluginState {
    DISCOVERED,
    LOADING,
    LOADED,
    STARTING,
    RUNNING,
    DRAINING,
    STOPPING,
    STOPPED,
    FAILED,
    UNLOADED;

    private static final Map<PluginState, Set<PluginState>> TRANSITIONS = new EnumMap<>(PluginState.class);

    static {
        TRANSITIONS.put(DISCOVERED, EnumSet.of(LOADING, FAILED));
        TRANSITIONS.put(LOADING, EnumSet.of(LOADED, FAILED));
        TRANSITIONS.put(LOADED, EnumSet.of(STARTING, STOPPED, FAILED));
        TRANSITIONS.put(STARTING, EnumSet.of(RUNNING, FAILED));
        TRANSITIONS.put(RUNNING, EnumSet.of(DRAINING, STOPPING, FAILED));
        TRANSITIONS.put(DRAINING, EnumSet.of(STOPPING, FAILED));
        TRANSITIONS.put(STOPPING, EnumSet.of(STOPPED, FAILED));
        TRANSITIONS.put(STOPPED, EnumSet.of(STARTING, UNLOADED, FAILED));
        TRANSITIONS.put(FAILED, EnumSet.of(UNLOADED));
        TRANSITIONS.put(UNLOADED, EnumSet.noneOf(PluginState.class));
    }

    public boolean canTransitionTo(PluginState target) {
        Set<PluginState> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
