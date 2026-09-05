package com.hsin.sms.plugin;

/** A send attempt exceeded the caller-configured timeout. */
public class PluginTimeoutException extends PluginException {

    public PluginTimeoutException(String pluginId, String version, String message) {
        super(pluginId, version, "send", message, null);
    }
}
