package com.hsin.sms.plugin;

import java.util.Objects;

/**
 * Decides whether a plugin's declared SPI version is compatible with the runtime.
 *
 * <p>Policy without an explicit range: same major version, and the runtime SPI minor
 * must be at least the plugin SPI minor (backward compatible host). An explicit
 * {@code requiresSpi} range in plugin.json, when present, takes precedence.</p>
 */
public final class SpiCompatibilityChecker {

    public record CompatibilityResult(boolean compatible, String reason) {
        static CompatibilityResult ok() {
            return new CompatibilityResult(true, "");
        }
    }

    private final SpiVersion runtimeSpiVersion;

    public SpiCompatibilityChecker(SpiVersion runtimeSpiVersion) {
        this.runtimeSpiVersion = Objects.requireNonNull(runtimeSpiVersion, "runtimeSpiVersion");
    }

    public SpiVersion runtimeSpiVersion() {
        return runtimeSpiVersion;
    }

    public CompatibilityResult check(SpiVersion pluginSpiVersion, String explicitRange) {
        Objects.requireNonNull(pluginSpiVersion, "pluginSpiVersion");
        if (explicitRange != null && !explicitRange.isBlank()) {
            try {
                SpiVersionRange range = SpiVersionRange.parse(explicitRange);
                return range.supports(runtimeSpiVersion)
                        ? CompatibilityResult.ok()
                        : new CompatibilityResult(false,
                        "runtime SPI " + runtimeSpiVersion + " is not inside required range " + range);
            } catch (IllegalArgumentException e) {
                return new CompatibilityResult(false, "invalid requiresSpi range: " + explicitRange);
            }
        }
        if (pluginSpiVersion.major() != runtimeSpiVersion.major()) {
            return new CompatibilityResult(false,
                    "plugin SPI major " + pluginSpiVersion.major()
                            + " differs from runtime SPI major " + runtimeSpiVersion.major());
        }
        if (pluginSpiVersion.compareTo(runtimeSpiVersion) > 0) {
            return new CompatibilityResult(false,
                    "plugin requires SPI " + pluginSpiVersion + " but runtime provides only "
                            + runtimeSpiVersion);
        }
        return CompatibilityResult.ok();
    }
}
