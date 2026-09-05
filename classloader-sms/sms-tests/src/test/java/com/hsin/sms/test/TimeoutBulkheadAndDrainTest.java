package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginBulkheadRejectedException;
import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.plugin.PluginRuntimeSettings;
import com.hsin.sms.plugin.PluginSnapshot;
import com.hsin.sms.plugin.PluginState;
import com.hsin.sms.plugin.PluginTimeoutException;
import com.hsin.sms.spi.SmsRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeoutBulkheadAndDrainTest {

    @Test
    void sendTimeoutInterruptsSlowProviderAndIsCounted() throws Exception {
        PluginManager manager = new PluginManager();
        try (manager) {
            manager.loadPlugin(Plugins.stagedDir("plugin-slow"));
            manager.startPlugin("plugin-slow");

            SmsRequest fast = SmsRequest.builder().requestId("timeout")
                    .phoneNumbers("13800138000").content("x")
                    .timeout(Duration.ofMillis(80)).build();
            assertThrows(PluginTimeoutException.class,
                    () -> manager.send("plugin-slow", fast));

            // worker receives the interrupt and exits quickly
            await(() -> manager.getPlugin("plugin-slow").inFlightRequests() == 0);
            assertEquals(1, manager.getPlugin("plugin-slow").metrics().timeoutCount());
        }
    }

    @Test
    void bulkheadRejectsSecondSlowCallWhileFirstIsInFlight() throws Exception {
        PluginRuntimeSettings settings = PluginRuntimeSettings.builder()
                .maxConcurrency(1)
                .bulkheadWaitTimeout(Duration.ofMillis(150))
                .build();
        try (PluginManager manager = new PluginManager(settings)) {
            manager.loadPlugin(Plugins.stagedDir("plugin-slow"));
            manager.startPlugin("plugin-slow");

            SmsRequest slow = SmsRequest.builder().requestId("first")
                    .phoneNumbers("13800138000").content("x")
                    .timeout(Duration.ofSeconds(10)).build();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                CompletableFuture<?> first = CompletableFuture.runAsync(
                        () -> manager.send("plugin-slow", slow), executor);
                await(() -> manager.getPlugin("plugin-slow").inFlightRequests() >= 1);
                SmsRequest second = SmsRequest.builder().requestId("second")
                        .phoneNumbers("13800138000").content("x")
                        .timeout(Duration.ofSeconds(10)).build();
                assertThrows(PluginBulkheadRejectedException.class,
                        () -> manager.send("plugin-slow", second));
                first.get(5, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            assertEquals(1, manager.getPlugin("plugin-slow").metrics().bulkheadRejectedCount());
        }
    }

    @Test
    void gracefulStopDrainsInFlightRequest() throws Exception {
        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(Plugins.stagedDir("plugin-slow"));
            manager.startPlugin("plugin-slow");
            SmsRequest slow = SmsRequest.builder().requestId("drain")
                    .phoneNumbers("13800138000").content("x")
                    .timeout(Duration.ofSeconds(10)).build();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                CompletableFuture<?> send = CompletableFuture.runAsync(
                        () -> manager.send("plugin-slow", slow), executor);
                await(() -> manager.getPlugin("plugin-slow").inFlightRequests() == 1);

                long started = System.nanoTime();
                PluginSnapshot stopped = manager.stopPlugin("plugin-slow",
                        Duration.ofSeconds(3), Duration.ofSeconds(3));
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertEquals(PluginState.STOPPED, stopped.state());
                assertTrue(elapsedMillis < 2900, "drain should wait for in-flight, elapsed="
                        + elapsedMillis);
                send.get(2, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            assertEquals(0, manager.getPlugin("plugin-slow").inFlightRequests());
            assertTrue(manager.getPlugin("plugin-slow").metrics().successCount() >= 1);
        }
    }

    private static void await(Checked condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.test()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5s");
            }
            Thread.sleep(10);
        }
    }

    @FunctionalInterface
    private interface Checked {
        boolean test() throws InterruptedException;
    }
}
