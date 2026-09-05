package com.hsin.sms.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Optional SHA-256 sidecar integrity policy.
 *
 * <p>When a plugin directory contains {@code integrity.sha256}, each non-comment
 * line must be {@code <sha256-hex> <relative-file>}. All listed files are verified
 * before the class loader is opened. Directories without the sidecar are skipped,
 * so {@link PluginIntegrityPolicy#ALWAYS_TRUST} remains the default.</p>
 */
public final class Sha256IntegrityPolicy implements PluginIntegrityPolicy {

    @Override
    public void verify(PluginDescriptor descriptor) {
        Path dir = descriptor.pluginDir();
        Path sidecar = dir.resolve("integrity.sha256");
        if (!Files.isRegularFile(sidecar)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(sidecar, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PluginLoadException(descriptor.id(), descriptor.version(),
                    "cannot read integrity.sha256: " + e.getMessage(), e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length != 2) {
                throw new PluginLoadException(descriptor.id(), descriptor.version(),
                        "invalid integrity.sha256 line " + (i + 1)
                                + ": expected '<sha256> <path>'", null);
            }
            String expected = parts[0];
            Path target = dir.resolve(parts[1]).normalize();
            if (!target.startsWith(dir)) {
                throw new PluginLoadException(descriptor.id(), descriptor.version(),
                        "integrity entry escapes plugin directory: " + parts[1], null);
            }
            if (!Files.isRegularFile(target)) {
                throw new PluginLoadException(descriptor.id(), descriptor.version(),
                        "integrity entry targets missing file: " + parts[1], null);
            }
            String actual = FingerprintUtil.sha256(target);
            if (!expected.equalsIgnoreCase(actual)) {
                throw new PluginLoadException(descriptor.id(), descriptor.version(),
                        "SHA-256 mismatch for " + parts[1]
                                + "; expected " + expected + " but got " + actual, null);
            }
        }
    }
}
