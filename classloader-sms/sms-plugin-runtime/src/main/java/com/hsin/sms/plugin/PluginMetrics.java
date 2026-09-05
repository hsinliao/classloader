package com.hsin.sms.plugin;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Plugin-scoped metrics. All counters are concurrency-safe atomics/long-adders.
 */
public final class PluginMetrics {

    private final LongAdder loadCount = new LongAdder();
    private final LongAdder loadFailureCount = new LongAdder();
    private final LongAdder startCount = new LongAdder();
    private final LongAdder startFailureCount = new LongAdder();
    private final LongAdder stopCount = new LongAdder();
    private final LongAdder stopFailureCount = new LongAdder();
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder timeoutCount = new LongAdder();
    private final LongAdder bulkheadRejectedCount = new LongAdder();
    private final AtomicLong lastErrorAtMillis = new AtomicLong();
    private final AtomicLong lastStateChangeAtMillis = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastLoadDurationMillis = new AtomicLong();
    private final AtomicLong lastStartDurationMillis = new AtomicLong();
    private final AtomicLong lastStopDurationMillis = new AtomicLong();
    private volatile String lastError;

    public void recordLoaded(long nanos) {
        loadCount.increment();
        lastLoadDurationMillis.set(nanos / 1_000_000);
    }

    public void recordLoadFailure(Throwable t) {
        loadFailureCount.increment();
        setLastError(t);
    }

    public void recordStarted(long nanos) {
        startCount.increment();
        lastStartDurationMillis.set(nanos / 1_000_000);
        lastError = null;
    }

    public void recordStartFailure(Throwable t) {
        startFailureCount.increment();
        setLastError(t);
    }

    public void recordStopped(long nanos) {
        stopCount.increment();
        lastStopDurationMillis.set(nanos / 1_000_000);
    }

    public void recordStopFailure(Throwable t) {
        stopFailureCount.increment();
        setLastError(t);
    }

    public void recordRequest() {
        requestCount.increment();
    }

    public void recordSuccess() {
        successCount.increment();
    }

    public void recordFailure(Throwable t) {
        failureCount.increment();
        setLastError(t);
    }

    public void recordTimeout() {
        timeoutCount.increment();
    }

    public void recordBulkheadRejected() {
        bulkheadRejectedCount.increment();
    }

    public void recordStateChange() {
        lastStateChangeAtMillis.set(System.currentTimeMillis());
    }

    public void recordUnexpectedThreadFailure(Throwable t) {
        failureCount.increment();
        setLastError(t);
    }

    private void setLastError(Throwable t) {
        lastError = t.getClass().getSimpleName() + ": "
                + (t.getMessage() == null ? "" : t.getMessage());
        lastErrorAtMillis.set(System.currentTimeMillis());
    }

    public PluginMetricsSnapshot snapshot() {
        return new PluginMetricsSnapshot(
                loadCount.sum(), loadFailureCount.sum(),
                startCount.sum(), startFailureCount.sum(),
                stopCount.sum(), stopFailureCount.sum(),
                requestCount.sum(), successCount.sum(), failureCount.sum(),
                timeoutCount.sum(), bulkheadRejectedCount.sum(),
                lastError, lastErrorAtMillis.get(), lastStateChangeAtMillis.get(),
                lastLoadDurationMillis.get(), lastStartDurationMillis.get(),
                lastStopDurationMillis.get());
    }
}
