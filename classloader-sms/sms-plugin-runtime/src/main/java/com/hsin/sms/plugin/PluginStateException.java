package com.hsin.sms.plugin;

/** An operation was refused because of the plugin's current lifecycle state. */
public class PluginStateException extends PluginException {

    public PluginStateException(String pluginId, String version, String message) {
        super(pluginId, version, "state", message, null);
    }
}
