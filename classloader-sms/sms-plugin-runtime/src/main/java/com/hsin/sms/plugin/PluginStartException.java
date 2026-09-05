package com.hsin.sms.plugin;

/** Failure while starting a plugin (provider creation/init). */
public class PluginStartException extends PluginException {

    public PluginStartException(String pluginId, String version, String message, Throwable cause) {
        super(pluginId, version, "start", message, cause);
    }
}
