package com.hsin.sms.demo;

import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.plugin.PluginRuntimeSettings;
import com.hsin.sms.plugin.PluginSnapshot;
import com.hsin.sms.service.DefaultTenantRouter;
import com.hsin.sms.service.ProviderRegistry;
import com.hsin.sms.service.SmsService;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runnable lifecycle demo:
 *
 * <pre>
 * scan/load A and B -> start A and B -> send via router -> inspect state
 * -> stop/unload A -> reload/start A -> send again -> shutdown
 * </pre>
 *
 * Plugin directories must be built first with {@code mvn clean package}; the demo
 * finds them under {@code classloader-sms/sms-test-plugins/plugin-a/target/plugin}.
 */
public final class SmsDemo {

    public static void main(String[] args) throws Exception {
        Path pluginA = pluginDir("plugin-a");
        Path pluginB = pluginDir("plugin-b");

        PluginManager manager = new PluginManager(
                PluginRuntimeSettings.builder().maxConcurrency(16).build());
        ProviderRegistry providerRegistry = new ProviderRegistry(manager);
        manager.addLifecycleListener(providerRegistry);
        DefaultTenantRouter router = new DefaultTenantRouter(
                Map.of("tenant-a", "plugin-a", "tenant-b", "plugin-b"), "plugin-a");
        SmsService service = new SmsService(manager, router, providerRegistry);

        println("== 1. load plugins ==");
        manager.loadPlugin(pluginA);
        manager.loadPlugin(pluginB);

        println("== 2. start plugins ==");
        manager.startPlugin("plugin-a");
        manager.startPlugin("plugin-b");

        println("== 3. send via router ==");
        SmsRequest request = SmsRequest.builder()
                .businessId("order-42")
                .phoneNumbers("13800000001")
                .content("verification code: 123456")
                .timeout(java.time.Duration.ofSeconds(5))
                .build();
        SmsResult resultA = service.sendForTenant("tenant-a", request);
        SmsResult resultB = service.sendForTenant("tenant-b", request);
        println("tenant-a -> " + resultA);
        println("tenant-b -> " + resultB);

        println("== 4. plugin state ==");
        for (PluginSnapshot snapshot : manager.getPlugins()) {
            println(snapshot.describe());
        }

        println("== 5. stop and unload plugin-a ==");
        manager.stopPlugin("plugin-a");
        manager.unloadPlugin("plugin-a");

        SmsResult afterUnload = service.sendForTenant("tenant-a", request);
        println("send after unload -> " + afterUnload);

        println("== 6. reload and restart plugin-a ==");
        manager.loadPlugin(pluginA);
        manager.startPlugin("plugin-a");
        SmsResult again = service.sendForTenant("tenant-a", request);
        println("send after reload -> " + again);

        println("== 7. shutdown ==");
        manager.close();
        println("done.");
    }

    private static Path pluginDir(String pluginId) {
        Path root = locatePluginsRoot();
        if (Files.isDirectory(root.resolve("sms-plugin-parent"))) {
            root = root.resolve("sms-plugin-parent");
        }
        Path dir = root.resolve(pluginId).resolve("target").resolve("plugin");
        if (!Files.isRegularFile(dir.resolve("plugin.json"))) {
            throw new IllegalStateException("plugin not staged at " + dir
                    + "; run 'mvn clean package' first");
        }
        return dir;
    }

    private static Path locatePluginsRoot() {
        String property = System.getProperty("sms.plugins.root");
        if (property != null && !property.isBlank()) {
            return Path.of(property).toAbsolutePath().normalize();
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
        throw new IllegalStateException(
                "cannot locate sms-test-plugins; use -Dsms.plugins.root=...");
    }

    private static void println(String line) {
        System.out.println(line);
    }

    private SmsDemo() {
        throw new AssertionError();
    }
}
