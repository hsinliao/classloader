package com.hsin.sms.plugin;

/**
 * Base exception for all plugin-runtime failures. Every failure carries the plugin
 * coordinates and the failing operation so diagnostics never require stack scraping.
 */
public class PluginException extends RuntimeException {

    private final String pluginId;
    private final String pluginVersion;
    private final String operation;

    public PluginException(String pluginId, String pluginVersion, String operation,
                           String message, Throwable cause) {
        super(message, cause);
        this.pluginId = pluginId;
        this.pluginVersion = pluginVersion;
        this.operation = operation;
    }

    public PluginException(String message) {
        this(null, null, null, message, null);
    }

    public PluginException(String message, Throwable cause) {
        this(null, null, null, message, cause);
    }

    public String pluginId() {
        return pluginId;
    }

    public String pluginVersion() {
        return pluginVersion;
    }

    public String operation() {
        return operation;
    }
}
