package com.hsin.sms.plugin;

/** Immutable metrics view. */
public record PluginMetricsSnapshot(
        long loadCount,
        long loadFailureCount,
        long startCount,
        long startFailureCount,
        long stopCount,
        long stopFailureCount,
        long requestCount,
        long successCount,
        long failureCount,
        long timeoutCount,
        long bulkheadRejectedCount,
        String lastError,
        long lastErrorAtMillis,
        long lastStateChangeAtMillis,
        long lastLoadDurationMillis,
        long lastStartDurationMillis,
        long lastStopDurationMillis) {
}
