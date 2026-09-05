package com.hsin.sms.plugin;

import com.hsin.sms.plugin.internal.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses and validates a plugin directory:
 * {@code plugin.json} + root plugin jar(s) + optional {@code lib/*.jar}.
 */
public final class PluginDescriptorParser {

    private static final Pattern PLUGIN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+){0,3}([-+][0-9A-Za-z.-]+)?");
    private static final Pattern JAVA_VERSION = Pattern.compile("\\d{2,3}");

    /**
     * Parses {@code plugin.json} and validates the directory layout.
     *
     * @throws PluginDescriptorException when metadata is malformed or the directory
     *         does not contain any root-level plugin jar
     */
    public PluginDescriptor parse(Path pluginDir) {
        Path dir = normalize(pluginDir);
        Path metadataFile = dir.resolve("plugin.json");
        if (!Files.isRegularFile(metadataFile)) {
            throw new PluginDescriptorException(
                    "plugin directory " + dir + " has no plugin.json");
        }
        Map<String, Object> root;
        try {
            String content = Files.readString(metadataFile, StandardCharsets.UTF_8);
            root = Json.parseObject(content);
        } catch (IOException e) {
            throw new PluginDescriptorException("cannot read " + metadataFile + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new PluginDescriptorException(
                    "plugin.json is not valid JSON in " + dir + ": " + e.getMessage(), e);
        }

        String id = requiredString(root, "id", dir);
        if (!PLUGIN_ID.matcher(id).matches()) {
            throw new PluginDescriptorException(
                    "plugin id '" + id + "' does not match " + PLUGIN_ID.pattern());
        }
        String name = requiredString(root, "name", dir);
        String version = requiredString(root, "version", dir);
        if (!VERSION.matcher(version).matches()) {
            throw new PluginDescriptorException("invalid plugin version '" + version + "' in " + dir);
        }
        String vendor = requiredString(root, "vendor", dir);
        String mainClass = requiredString(root, "mainClass", dir);
        String spiText = requiredString(root, "spiVersion", dir);
        SpiVersion spi;
        try {
            spi = SpiVersion.parse(spiText);
        } catch (IllegalArgumentException e) {
            throw new PluginDescriptorException("invalid spiVersion '" + spiText + "' in " + dir, e);
        }
        String requiresJava = optionalString(root, "requiresJava", "17");
        if (!JAVA_VERSION.matcher(requiresJava).matches()) {
            throw new PluginDescriptorException("invalid requiresJava '" + requiresJava + "' in " + dir);
        }
        String requiresSpiRange = optionalString(root, "requiresSpi", null);
        if (requiresSpiRange != null) {
            try {
                SpiVersionRange.parse(requiresSpiRange);
            } catch (IllegalArgumentException e) {
                throw new PluginDescriptorException(
                        "invalid requiresSpi '" + requiresSpiRange + "' in " + dir, e);
            }
        }

        List<Path> rootJars = listJars(dir, false);
        if (rootJars.isEmpty()) {
            throw new PluginDescriptorException("no plugin jar found at top level of " + dir);
        }
        List<Path> libs = listJars(dir.resolve("lib"), false);
        return new PluginDescriptor(id, name, version, vendor, mainClass, spi,
                requiresJava, requiresSpiRange, dir.toAbsolutePath().normalize(),
                List.copyOf(rootJars), List.copyOf(libs));
    }

    private static Path normalize(Path pluginDir) {
        Path dir = pluginDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new PluginDescriptorException("plugin directory does not exist: " + dir);
        }
        return dir;
    }

    private static String requiredString(Map<String, Object> root, String key, Path dir) {
        Object v = root.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new PluginDescriptorException(
                    "plugin.json in " + dir + " is missing required string field '" + key + "'");
        }
        return s.trim();
    }

    private static String optionalString(Map<String, Object> root, String key, String defaultValue) {
        Object v = root.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (!(v instanceof String s) || s.isBlank()) {
            throw new PluginDescriptorException("plugin.json field '" + key + "' must be a string");
        }
        return s.trim();
    }

    private static List<Path> listJars(Path dir, boolean mustExist) {
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            if (mustExist) {
                throw new PluginDescriptorException("required jar directory does not exist: " + dir);
            }
            return jars;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    jars.add(p.toAbsolutePath().normalize());
                }
            }
        } catch (IOException e) {
            throw new PluginDescriptorException("cannot list jars in " + dir + ": " + e.getMessage(), e);
        }
        jars.sort(Path::compareTo);
        return jars;
    }

    public PluginDescriptorParser() {
    }
}
