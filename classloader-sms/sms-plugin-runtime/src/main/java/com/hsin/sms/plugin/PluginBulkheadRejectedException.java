package com.hsin.sms.plugin;

/** The plugin bulkhead refused a request because all permits were busy. */
public class PluginBulkheadRejectedException extends PluginException {

    public PluginBulkheadRejectedException(String pluginId, String version, String message) {
        super(pluginId, version, "send", message, null);
    }
}
