package com.hsin.sms.plugin;

/** Failure while preparing a plugin runtime (classloader, discovery, integrity). */
public class PluginLoadException extends PluginException {

    public PluginLoadException(String pluginId, String version, String message, Throwable cause) {
        super(pluginId, version, "load", message, cause);
    }
}
