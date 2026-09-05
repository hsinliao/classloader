package com.hsin.sms.plugin;

/** No loaded runtime exists for the requested plugin id. */
public class PluginNotFoundException extends PluginException {

    public PluginNotFoundException(String pluginId) {
        super(pluginId, null, "lookup", "no plugin with id '" + pluginId + "' is loaded", null);
    }
}
