package com.hsin.sms.plugin;

/** A plugin was rejected because of SPI or JVM version incompatibility. */
public class PluginCompatibilityException extends PluginException {

    public PluginCompatibilityException(String pluginId, String version, String message) {
        super(pluginId, version, "check-compatibility", message, null);
    }
}
