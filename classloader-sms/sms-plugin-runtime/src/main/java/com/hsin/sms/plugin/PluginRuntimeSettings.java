package com.hsin.sms.plugin;

import java.time.Duration;

/** Immutable host-side tuning knobs for plugin runtime instances. */
public final class PluginRuntimeSettings {

    private final SpiVersion runtimeSpiVersion;
    private final int maxConcurrency;
    private final Duration bulkheadWaitTimeout;
    private final Duration drainTimeout;
    private final Duration shutdownTimeout;
    private final Duration stopProviderTimeout;

    private PluginRuntimeSettings(Builder b) {
        this.runtimeSpiVersion = b.runtimeSpiVersion;
        this.maxConcurrency = b.maxConcurrency;
        this.bulkheadWaitTimeout = b.bulkheadWaitTimeout;
        this.drainTimeout = b.drainTimeout;
        this.shutdownTimeout = b.shutdownTimeout;
        this.stopProviderTimeout = b.stopProviderTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PluginRuntimeSettings defaults() {
        return builder().build();
    }

    public SpiVersion runtimeSpiVersion() {
        return runtimeSpiVersion;
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

    public Duration bulkheadWaitTimeout() {
        return bulkheadWaitTimeout;
    }

    public Duration drainTimeout() {
        return drainTimeout;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    public Duration stopProviderTimeout() {
        return stopProviderTimeout;
    }

    public static final class Builder {
        private SpiVersion runtimeSpiVersion = SpiVersion.of(1, 0);
        private int maxConcurrency = 32;
        private Duration bulkheadWaitTimeout = Duration.ofSeconds(5);
        private Duration drainTimeout = Duration.ofSeconds(30);
        private Duration shutdownTimeout = Duration.ofSeconds(30);
        private Duration stopProviderTimeout = Duration.ofSeconds(10);

        public Builder runtimeSpiVersion(SpiVersion v) {
            this.runtimeSpiVersion = v;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            if (maxConcurrency < 1) {
                throw new IllegalArgumentException("maxConcurrency must be >= 1");
            }
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder bulkheadWaitTimeout(Duration d) {
            this.bulkheadWaitTimeout = d;
            return this;
        }

        public Builder drainTimeout(Duration d) {
            this.drainTimeout = d;
            return this;
        }

        public Builder shutdownTimeout(Duration d) {
            this.shutdownTimeout = d;
            return this;
        }

        public Builder stopProviderTimeout(Duration d) {
            this.stopProviderTimeout = d;
            return this;
        }

        public PluginRuntimeSettings build() {
            return new PluginRuntimeSettings(this);
        }
    }
}
