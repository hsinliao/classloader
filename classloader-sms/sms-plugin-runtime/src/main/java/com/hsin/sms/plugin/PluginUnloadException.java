package com.hsin.sms.plugin;

/** Failure while releasing a plugin class loader and its resources. */
public class PluginUnloadException extends PluginException {

    public PluginUnloadException(String pluginId, String version, String message, Throwable cause) {
        super(pluginId, version, "unload", message, cause);
    }
}
