package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.plugin.PluginResourceRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadAndResourceLifecycleTest {

    @Test
    void resourceRegistryClosesReverseOrderAndIsIdempotent() throws Exception {
        PluginResourceRegistry registry = new PluginResourceRegistry();
        List<String> closed = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        registry.register("first", () -> {
            closed.add("first");
            latch.countDown();
        });
        registry.register("second", () -> closed.add("second"));
        registry.register("third", () -> closed.add("third"));

        registry.closeAll();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(List.of("third", "second", "first"), closed);
        assertEquals(0, registry.size());
        registry.closeAll(); // no-op
    }

    @Test
    void pluginOwnedThreadsAndExecutorsAreGoneAfterUnload() throws Exception {
        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(Plugins.stagedDir("plugin-threads"));
            manager.startPlugin("plugin-threads");
            String prefix = "sms-plugin-plugin-threads-";

            await(() -> threadNames().stream().anyMatch(name -> name.startsWith("manual-loop")));
            assertTrue(manager.getPlugin("plugin-threads").registeredResources().stream()
                    .anyMatch(name -> name.contains("manual-thread")));
            assertTrue(manager.getPlugin("plugin-threads").registeredResourceCount() >= 3);
            assertTrue(threadNames().stream().anyMatch(n -> n.startsWith("manual-loop")
                    || n.startsWith(prefix)));

            manager.stopPlugin("plugin-threads");
            assertEquals(0, manager.getPlugin("plugin-threads").registeredResourceCount());
            manager.unloadPlugin("plugin-threads");

            await(() -> threadNames().stream().noneMatch(n -> n.startsWith(prefix)));
            assertFalse(threadNames().stream().anyMatch(n -> n.startsWith(prefix)));
        }
    }

    @Test
    void resourcePluginRegistersResourcesAndStopClosesThem() throws Exception {
        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(Plugins.stagedDir("plugin-resources"));
            manager.startPlugin("plugin-resources");
            assertEquals(5, manager.getPlugin("plugin-resources").registeredResourceCount());
            manager.stopPlugin("plugin-resources");
            assertEquals(0, manager.getPlugin("plugin-resources").registeredResourceCount());
            assertTrue(manager.getPlugin("plugin-resources").registeredResources().isEmpty());
        }
    }

    private static Set<String> threadNames() {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static void await(Checked condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.test()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 8s");
            }
            Thread.sleep(20);
        }
    }

    @FunctionalInterface
    private interface Checked {
        boolean test() throws InterruptedException;
    }
}
