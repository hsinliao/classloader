package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.plugin.PluginSnapshot;
import com.hsin.sms.plugin.PluginState;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLifecycleTest {

    @Test
    void fullLifecycleLoadStartSendStopRestartUnload() throws Exception {
        Path dir = Plugins.stagedDir("plugin-a");
        try (PluginManager manager = new PluginManager()) {
            PluginSnapshot loaded = manager.loadPlugin(dir);
            assertEquals(PluginState.LOADED, loaded.state());
            assertTrue(loaded.metrics().loadCount() >= 1);

            manager.startPlugin("plugin-a");
            PluginSnapshot running = manager.getPlugin("plugin-a");
            assertEquals(PluginState.RUNNING, running.state());
            assertNotNull(running.providerClassName());

            SmsRequest request = SmsRequest.builder()
                    .requestId("r1").phoneNumbers("13800138000").content("hello").build();
            SmsResponse response = manager.send("plugin-a", request);
            assertTrue(response.isSuccessful());
            assertEquals("1.0", response.extensions().get("libraryVersion"));
            assertEquals("cn-shanghai", response.extensions().get("region"));

            PluginSnapshot stopped = manager.stopPlugin("plugin-a");
            assertEquals(PluginState.STOPPED, stopped.state());

            manager.startPlugin("plugin-a");
            SmsResponse restarted = manager.send("plugin-a", request);
            assertTrue(restarted.isSuccessful());

            PluginSnapshot unloaded = manager.unloadPlugin("plugin-a");
            assertEquals(PluginState.UNLOADED, unloaded.state());
            assertFalse(manager.isLoaded("plugin-a"));
            assertTrue(manager.getPlugins().isEmpty());
        }
    }

    @Test
    void stopBeforeStartMovesFromLoadedToStopped() {
        Path dir = Plugins.stagedDir("plugin-b");
        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(dir);
            manager.stopPlugin("plugin-b");
            assertEquals(PluginState.STOPPED, manager.getPlugin("plugin-b").state());
            manager.startPlugin("plugin-b");
            assertEquals(PluginState.RUNNING, manager.getPlugin("plugin-b").state());
            assertTrue(manager.send("plugin-b",
                    SmsRequest.builder().requestId("r2").phoneNumbers("13800138000")
                            .content("x").timeout(Duration.ofSeconds(2)).build()).isSuccessful());
        }
    }
}
