package com.hsin.sms.plugin;

import java.nio.file.Path;
import java.util.Map;

/** Loads a plugin's {@code config/} files into an immutable string map. */
public interface PluginConfigSource {

    Map<String, String> load(Path pluginDirectory);

    static PluginConfigSource empty() {
        return dir -> Map.of();
    }
}
