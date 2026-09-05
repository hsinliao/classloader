package com.hsin.sms.plugin;

/** Extension point for artifact integrity/signature verification. */
public interface PluginIntegrityPolicy {

    PluginIntegrityPolicy ALWAYS_TRUST = descriptor -> { };

    /** Throws {@link PluginLoadException} when the plugin artifacts are not trusted. */
    void verify(PluginDescriptor descriptor);
}
