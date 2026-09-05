package com.hsin.sms.plugin;

import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;

import java.util.List;

/**
 * Narrow facade the business/service layer uses to reach plugins. It intentionally
 * hides class loaders, lifecycle internals and the plugin registry.
 */
public interface PluginGateway {

    /** Sends a message through the named plugin; runtime policy applies. */
    SmsResponse send(String pluginId, SmsRequest request);

    /** Current snapshot of a plugin, or {@code null}. */
    PluginSnapshot getPlugin(String pluginId);

    /** All loaded plugin snapshots. */
    List<PluginSnapshot> getPlugins();
}
