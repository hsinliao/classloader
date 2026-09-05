package com.hsin.sms.plugin;

/** A provider invocation failed (provider exception, linkage error, etc.). */
public class PluginInvocationException extends PluginException {

    private final boolean retryable;

    public PluginInvocationException(String pluginId, String version, boolean retryable,
                                     String message, Throwable cause) {
        super(pluginId, version, "send", message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
