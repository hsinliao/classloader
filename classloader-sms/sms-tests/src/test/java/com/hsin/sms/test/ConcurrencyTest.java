package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.plugin.PluginRuntimeSettings;
import com.hsin.sms.service.DefaultTenantRouter;
import com.hsin.sms.service.ProviderRegistry;
import com.hsin.sms.service.SmsService;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    @Test
    void concurrentLoadAndSend() throws Exception {
        try (PluginManager manager = new PluginManager(
                PluginRuntimeSettings.builder().maxConcurrency(8).build())) {
            int threads = 6;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int index = i;
                tasks.add(pool.submit(() -> {
                    try {
                        start.await();
                        if (index % 2 == 0) {
                            manager.loadPlugin(Plugins.stagedDir("plugin-a"));
                            manager.startPlugin("plugin-a");
                        } else {
                            manager.loadPlugin(Plugins.stagedDir("plugin-b"));
                            manager.startPlugin("plugin-b");
                        }
                    } catch (Exception e) {
                        if (!manager.isLoaded("plugin-a") && !manager.isLoaded("plugin-b")) {
                            throw new RuntimeException(e);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            pool.shutdownNow();
            assertTrue(manager.isLoaded("plugin-a"));
            assertTrue(manager.isLoaded("plugin-b"));
            assertEquals(2, manager.getPlugins().size());
        }
    }

    @Test
    void reloadWhileSendingDoesNotDeadlockAndRecovers() throws Exception {
        try (PluginManager manager = new PluginManager(
                PluginRuntimeSettings.builder().maxConcurrency(8).build())) {
            ProviderRegistry registry = new ProviderRegistry(manager);
            manager.addLifecycleListener(registry);
            SmsService service = new SmsService(manager,
                    new DefaultTenantRouter(Map.of("t", "plugin-a")), registry);

            manager.loadPlugin(Plugins.stagedDir("plugin-a"));
            manager.loadPlugin(Plugins.stagedDir("plugin-b"));
            manager.startPlugin("plugin-a");
            manager.startPlugin("plugin-b");

            ExecutorService pool = Executors.newFixedThreadPool(6);
            AtomicInteger successes = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> senders = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                senders.add(pool.submit(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < 20; j++) {
                            SmsResult result = service.sendForTenant("t",
                                    SmsRequest.builder().requestId("c-" + j)
                                            .phoneNumbers("13800138000").content("x")
                                            .timeout(Duration.ofSeconds(3)).build());
                            if (result.success()) {
                                successes.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            Future<?> reloader = pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 3; i++) {
                        Thread.sleep(80);
                        manager.reloadPlugin("plugin-a");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            start.countDown();
            for (Future<?> sender : senders) {
                sender.get(30, TimeUnit.SECONDS);
            }
            reloader.get(30, TimeUnit.SECONDS);
            pool.shutdownNow();

            assertTrue(successes.get() > 0);
            assertTrue(manager.isLoaded("plugin-a"));
            SmsResult finalResult = service.sendForTenant("t",
                    SmsRequest.builder().requestId("final").phoneNumbers("13800138000")
                            .content("x").build());
            assertTrue(finalResult.success(), "final send failed: " + finalResult);
        }
    }
}
