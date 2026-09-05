package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginCompatibilityException;
import com.hsin.sms.plugin.PluginDescriptorException;
import com.hsin.sms.plugin.PluginLoadException;
import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.plugin.PluginStartException;
import com.hsin.sms.plugin.PluginState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Broken plugins must fail in isolation and never corrupt healthy plugins. */
class PluginFailureIsolationTest {

    @Test
    void brokenPluginsFailCleanlyAndHealthyPluginSurvives() {
        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(Plugins.stagedDir("plugin-a"));

            assertThrows(PluginDescriptorException.class,
                    () -> manager.loadPlugin(Plugins.stagedDir("broken-json")));
            assertThrows(PluginCompatibilityException.class,
                    () -> manager.loadPlugin(Plugins.stagedDir("broken-spi-version")));
            assertThrows(PluginLoadException.class,
                    () -> manager.loadPlugin(Plugins.stagedDir("broken-main-missing")));
            assertThrows(PluginLoadException.class,
                    () -> manager.loadPlugin(Plugins.stagedDir("broken-spi-missing")));

            manager.startPlugin("plugin-a");
            assertTrue(manager.send("plugin-a", com.hsin.sms.spi.SmsRequest.of("ok",
                    java.util.List.of("13800138000"), "still alive")).isSuccessful());
            assertEquals(1, manager.getPlugins().size());
        }
    }

    @Test
    void missingDependencyFailsAtStartAndRollsBack() {
        try (PluginManager manager = new PluginManager()) {
            PluginManager m = manager;
            m.loadPlugin(Plugins.stagedDir("broken-missing-dep"));
            PluginStartException ex = assertThrows(PluginStartException.class,
                    () -> m.startPlugin("broken-missing-dep"));
            Throwable cursor = ex;
            boolean linkage = false;
            while (cursor != null && cursor.getCause() != cursor) {
                linkage = linkage || cursor instanceof NoClassDefFoundError
                        || cursor instanceof ClassNotFoundException;
                cursor = cursor.getCause();
            }
            assertTrue(linkage, "expected linkage failure but was: " + ex);
            assertFalse(m.isLoaded("broken-missing-dep"));
        }
    }
}
