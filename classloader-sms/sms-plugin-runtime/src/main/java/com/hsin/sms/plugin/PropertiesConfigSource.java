package com.hsin.sms.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Reads {@code config/application.properties} from a plugin directory.
 * Missing config directory simply yields an empty map.
 */
public final class PropertiesConfigSource implements PluginConfigSource {

    @Override
    public Map<String, String> load(Path pluginDirectory) {
        Path file = pluginDirectory.resolve("config").resolve("application.properties");
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read plugin config " + file, e);
        }
        Map<String, String> result = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name.trim(), properties.getProperty(name).trim());
        }
        return Map.copyOf(result);
    }
}
