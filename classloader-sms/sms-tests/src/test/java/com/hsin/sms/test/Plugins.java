package com.hsin.sms.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/** Locates staged test plugins produced by {@code mvn package}. */
public final class Plugins {

    public static Path root() {
        String property = System.getProperty("sms.plugins.root");
        if (property != null && !property.isBlank()) {
            Path fromProperty = Path.of(property).toAbsolutePath().normalize();
            if (Files.isDirectory(fromProperty)) {
                return fromProperty;
            }
        }
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10 && current != null; i++) {
            Path candidate = current.resolve("classloader-sms").resolve("sms-test-plugins");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            candidate = current.resolve("sms-test-plugins");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate sms-test-plugins root");
    }

    public static Path stagedDir(String pluginId) {
        Path dir = root().resolve("sms-plugin-parent").resolve(pluginId)
                .resolve("target").resolve("plugin");
        if (!Files.isRegularFile(dir.resolve("plugin.json"))) {
            throw new IllegalStateException("plugin '" + pluginId + "' not staged at " + dir
                    + "; run 'mvn clean package' first");
        }
        return dir;
    }

    /** Copies selected staged plugins into a flat {@code <tmp>/plugins/id} layout. */
    public static Path flatRoot(String... pluginIds) throws IOException {
        Path root = Files.createTempDirectory("sms-plugins-scan-");
        for (String id : pluginIds) {
            copyTree(stagedDir(id), root.resolve(id));
        }
        return root;
    }

    public static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path p : walk.toList()) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Plugins() {
        throw new AssertionError();
    }
}
