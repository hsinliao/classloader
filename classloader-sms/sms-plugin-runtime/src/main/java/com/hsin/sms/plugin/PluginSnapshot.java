package com.hsin.sms.plugin;

import java.nio.file.Path;
import java.util.List;

/** Immutable, diagnostic-rich view of one plugin runtime. */
public record PluginSnapshot(
        String pluginId,
        String name,
        String version,
        String vendor,
        PluginState state,
        String spiVersion,
        String mainClass,
        String providerClassName,
        String classLoaderDescription,
        String providerClassLoaderDescription,
        List<String> loadedUrls,
        Path pluginDirectory,
        int registeredResourceCount,
        List<String> registeredResources,
        long inFlightRequests,
        long createdThreadCount,
        String lastError,
        PluginMetricsSnapshot metrics) {

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("pluginId          : ").append(pluginId).append('\n');
        sb.append("name              : ").append(name).append('\n');
        sb.append("version           : ").append(version).append('\n');
        sb.append("state             : ").append(state).append('\n');
        sb.append("spiVersion        : ").append(spiVersion).append('\n');
        sb.append("mainClass         : ").append(mainClass).append('\n');
        sb.append("providerClass     : ").append(nullToDash(providerClassName)).append('\n');
        sb.append("classLoader       : ").append(nullToDash(classLoaderDescription)).append('\n');
        sb.append("providerClassLoader: ").append(nullToDash(providerClassLoaderDescription)).append('\n');
        sb.append("pluginDirectory   : ").append(pluginDirectory).append('\n');
        sb.append("inFlight          : ").append(inFlightRequests).append('\n');
        sb.append("createdThreads    : ").append(createdThreadCount).append('\n');
        sb.append("resources         : ").append(registeredResourceCount).append(' ')
                .append(registeredResources).append('\n');
        sb.append("lastError         : ").append(nullToDash(lastError)).append('\n');
        sb.append("metrics           : ").append(metrics);
        return sb.toString();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
