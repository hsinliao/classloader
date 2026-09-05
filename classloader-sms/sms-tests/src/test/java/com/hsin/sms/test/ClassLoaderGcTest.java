package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GC observation is not deterministic, so this test unloads the plugin, releases all
 * strong references and then repeatedly triggers GC while polling a reference queue
 * for up to a bounded window. It observes eventual collection instead of asserting
 * after a single {@code System.gc()}.
 */
class ClassLoaderGcTest {

    @Test
    void unloadedPluginClassLoaderBecomesReachableForGc() throws Exception {
        PluginManager manager = new PluginManager();
        manager.loadPlugin(Plugins.stagedDir("plugin-a"));
        manager.startPlugin("plugin-a");

        ClassLoader loader = manager.pluginClassLoader("plugin-a");
        ReferenceQueue<ClassLoader> queue = new ReferenceQueue<>();
        WeakReference<ClassLoader> weak = new WeakReference<>(loader, queue);
        assertNotNull(loader);

        loader = null;
        manager.stopPlugin("plugin-a");
        manager.unloadPlugin("plugin-a");
        manager.close();

        List<byte[]> pressure = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        for (int i = 0; i < 200 && System.nanoTime() < deadline; i++) {
            if (i % 4 == 0) {
                pressure.add(new byte[512 * 1024]);
            }
            if (i % 16 == 0) {
                pressure.clear();
                System.gc();
            }
            if (queue.poll() != null) {
                assertSame(weak, weak); // class loader was enqueued
                assertTrue(weak.get() == null || weak.refersTo(null));
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("class loader was not collected within 10s; "
                + "use heap/jcmd class histogram to inspect references");
    }
}
