package com.hsin.sms.plugin;

/** Invalid, malformed or unreadable plugin metadata (plugin.json). */
public class PluginDescriptorException extends PluginException {

    public PluginDescriptorException(String message) {
        super(message);
    }

    public PluginDescriptorException(String message, Throwable cause) {
        super(message, cause);
    }

    public PluginDescriptorException(String pluginId, String version, String message, Throwable cause) {
        super(pluginId, version, "describe", message, cause);
    }
}
