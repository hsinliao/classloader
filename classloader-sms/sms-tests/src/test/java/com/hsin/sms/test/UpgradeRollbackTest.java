package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeRollbackTest {

    @Test
    void upgradeThenRollbackKeepsServiceAvailable() throws Exception {
        Path original = Plugins.stagedDir("plugin-a");
        Path upgraded = Files.createTempDirectory("plugin-a-upgrade-");
        Plugins.copyTree(original, upgraded);

        Path json = upgraded.resolve("plugin.json");
        String metadata = Files.readString(json, StandardCharsets.UTF_8)
                .replace("\"1.0.0\"", "\"1.1.0\"");
        Files.writeString(json, metadata, StandardCharsets.UTF_8);
        Path config = upgraded.resolve("config").resolve("application.properties");
        Files.writeString(config,
                Files.readString(config, StandardCharsets.UTF_8).replace("cn-shanghai", "cn-upgraded"),
                StandardCharsets.UTF_8);

        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(original);
            manager.startPlugin("plugin-a");
            assertEquals("cn-shanghai", send(manager, "region"));

            manager.upgradePlugin("plugin-a", upgraded);
            assertEquals("1.1.0", manager.getPlugin("plugin-a").version());
            assertEquals("cn-upgraded", send(manager, "region"));

            manager.rollbackPlugin("plugin-a");
            assertEquals("1.0.0", manager.getPlugin("plugin-a").version());
            assertEquals("cn-shanghai", send(manager, "region"));
        } finally {
            Plugins.deleteRecursively(upgraded);
        }
    }

    private static String send(PluginManager manager, String extension) {
        SmsRequest request = SmsRequest.builder().requestId("up")
                .phoneNumbers("13800138000").content("upgrade-test").build();
        SmsResponse response = manager.send("plugin-a", request);
        assertTrue(response.isSuccessful());
        return response.extensions().get(extension);
    }
}
