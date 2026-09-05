package com.hsin.sms.plugin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers plugin directories beneath a root (for example {@code plugins/}).
 * Each immediate child directory containing {@code plugin.json} is a candidate.
 */
public final class PluginScanner {

    private final PluginDescriptorParser parser = new PluginDescriptorParser();

    public record DiscoveryResult(PluginDescriptor descriptor, String error) {
        public boolean ok() {
            return descriptor != null;
        }
    }

    /**
     * Scans a directory whose immediate children are plugin directories. Each entry
     * is either a parsed descriptor or a non-fatal parse error; one bad directory
     * does not abort scanning of the others.
     */
    public List<DiscoveryResult> scan(Path pluginsRoot) {
        Path root = pluginsRoot.toAbsolutePath().normalize();
        List<DiscoveryResult> results = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return results;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child) || Files.isHidden(child)) {
                    continue;
                }
                if (!Files.isRegularFile(child.resolve("plugin.json"))) {
                    continue;
                }
                try {
                    results.add(new DiscoveryResult(parser.parse(child), null));
                } catch (PluginDescriptorException e) {
                    results.add(new DiscoveryResult(null, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new PluginException("cannot scan plugin root " + root + ": " + e.getMessage(), e);
        }
        results.sort((a, b) -> {
            String aId = a.ok() ? a.descriptor().id() : "";
            String bId = b.ok() ? b.descriptor().id() : "";
            return aId.compareTo(bId);
        });
        return results;
    }
}
