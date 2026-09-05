package com.hsin.sms.plugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Validated, immutable plugin metadata parsed from a plugin directory.
 */
public record PluginDescriptor(
        String id,
        String name,
        String version,
        String vendor,
        String mainClass,
        SpiVersion spiVersion,
        String requiresJava,
        String requiresSpiRange,
        Path pluginDir,
        List<Path> pluginJars,
        List<Path> libraryJars) {

    public List<Path> allJars() {
        return java.util.stream.Stream.concat(pluginJars.stream(), libraryJars.stream()).toList();
    }
}
