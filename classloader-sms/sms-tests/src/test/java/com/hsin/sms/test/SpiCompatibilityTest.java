package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.plugin.PluginLoadException;
import com.hsin.sms.plugin.PluginScanner;
import com.hsin.sms.plugin.SpiCompatibilityChecker;
import com.hsin.sms.plugin.SpiVersion;
import com.hsin.sms.plugin.SpiVersionRange;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiCompatibilityTest {

    @Test
    void versionModelAndRangeRules() {
        SpiVersion runtime = SpiVersion.of(1, 5);
        SpiCompatibilityChecker checker = new SpiCompatibilityChecker(runtime);
        assertTrue(checker.check(SpiVersion.of(1, 2), null).compatible());
        assertTrue(checker.check(SpiVersion.of(1, 5), null).compatible());
        assertFalse(checker.check(SpiVersion.of(2, 0), null).compatible());
        assertFalse(checker.check(SpiVersion.of(1, 6), null).compatible());

        SpiVersionRange range = SpiVersionRange.parse(">=1.2 <2.0");
        assertTrue(range.supports(SpiVersion.of(1, 5)));
        assertTrue(range.supports(SpiVersion.of(1, 2)));
        assertFalse(range.supports(SpiVersion.of(1, 1)));
        assertFalse(range.supports(SpiVersion.of(2, 0)));
        assertTrue(checker.check(SpiVersion.of(1, 0), ">=1.2 <2.0").compatible());
        assertEquals(1, SpiVersion.parse("1.2.3").major());
        assertEquals(2, SpiVersion.parse("1.2.3").minor());
    }

    @Test
    void scannerDiscoversValidDirectoriesAndReportsBadOnes() throws Exception {
        Path root = Plugins.flatRoot("plugin-a", "plugin-b", "broken-json");
        try {
            PluginScanner scanner = new PluginScanner();
            List<PluginScanner.DiscoveryResult> results = scanner.scan(root);
            assertEquals(2, results.stream().filter(PluginScanner.DiscoveryResult::ok).count());
            assertEquals(1, results.stream().filter(r -> !r.ok()).count());
            assertTrue(results.stream().filter(PluginScanner.DiscoveryResult::ok)
                    .anyMatch(r -> r.descriptor().id().equals("plugin-b")));
        } finally {
            Plugins.deleteRecursively(root);
        }
    }

    @Test
    void managerRejectsDuplicatePluginId() {
        try (PluginManager manager = new PluginManager()) {
            Path dir = Plugins.stagedDir("plugin-a");
            manager.loadPlugin(dir);
            assertTrue(manager.isLoaded("plugin-a"));
            assertThrows(PluginLoadException.class, () -> manager.loadPlugin(dir));
        }
    }
}
