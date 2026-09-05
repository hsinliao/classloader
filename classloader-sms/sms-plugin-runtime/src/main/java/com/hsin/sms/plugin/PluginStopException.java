package com.hsin.sms.plugin;

/** Failure while stopping/draining a plugin. */
public class PluginStopException extends PluginException {

    public PluginStopException(String pluginId, String version, String message, Throwable cause) {
        super(pluginId, version, "stop", message, cause);
    }
}
