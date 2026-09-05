package com.hsin.sms.test;

import com.hsin.sms.plugin.PluginManager;
import com.hsin.sms.spi.SmsProvider;
import com.hsin.sms.spi.SmsRequest;
import com.hsin.sms.spi.SmsResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real isolation checks: identical dependency FQCNs resolve to different classes in
 * different plugin class loaders, each version behaves differently, and the SPI API
 * itself is only ever the host copy.
 */
class ClassLoaderIsolationTest {

    private static final String DEP = "com.hsin.sms.testdep.LibraryInfo";

    @Test
    void differentDependencyVersionsRunSimultaneously() throws Exception {
        Path dirA = Plugins.stagedDir("plugin-a");
        Path dirB = Plugins.stagedDir("plugin-b");
        Path dirC = Plugins.stagedDir("plugin-conflict");

        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(dirA);
            manager.loadPlugin(dirB);
            manager.loadPlugin(dirC);
            manager.startPlugin("plugin-a");
            manager.startPlugin("plugin-b");
            manager.startPlugin("plugin-conflict");

            ClassLoader clA = manager.pluginClassLoader("plugin-a");
            ClassLoader clB = manager.pluginClassLoader("plugin-b");
            ClassLoader clC = manager.pluginClassLoader("plugin-conflict");
            assertNotSame(clA, clB);
            assertNotSame(clB, clC);
            assertNotSame(clA, clC);

            Class<?> depA = clA.loadClass(DEP);
            Class<?> depB = clB.loadClass(DEP);
            Class<?> depC = clC.loadClass(DEP);
            assertNotSame(depA, depB);
            assertNotSame(depB, depC);
            assertEquals("1.0", invoke(depA, "version"));
            assertEquals("2.0", invoke(depB, "version"));
            assertEquals("2.0", invoke(depC, "version"));
            assertEquals("vendor-v1-feature", invoke(depA, "feature"));
            assertEquals("vendor-v2-feature", invoke(depB, "feature"));

            // plugin main/provider classes are child-loaded
            Class<?> providerA = clA.loadClass("com.hsin.sms.plugins.demo.PluginAProvider");
            assertSame(clA, providerA.getClassLoader());
            Class<?> providerB = clB.loadClass("com.hsin.sms.plugins.demo.PluginBProvider");
            assertSame(clB, providerB.getClassLoader());

            // SPI API class is parent-loaded (host identity shared with the test)
            Class<?> spiFromA = clA.loadClass(SmsProvider.class.getName());
            assertSame(SmsProvider.class, spiFromA);
            assertNotSame(clA, SmsProvider.class.getClassLoader());

            SmsResponse responseA = manager.send("plugin-a", request("a"));
            SmsResponse responseB = manager.send("plugin-b", request("b"));
            SmsResponse responseC = manager.send("plugin-conflict", request("c"));
            assertEquals("1.0", responseA.extensions().get("libraryVersion"));
            assertEquals("2.0", responseB.extensions().get("libraryVersion"));
            assertEquals("2.0", responseC.extensions().get("libraryVersion"));
        }
    }

    @Test
    void sendBehaviorProvesEachPluginUsesItsOwnVersion() {
        try (PluginManager manager = new PluginManager()) {
            manager.loadPlugin(Plugins.stagedDir("plugin-a"));
            manager.loadPlugin(Plugins.stagedDir("plugin-b"));
            manager.startPlugin("plugin-a");
            manager.startPlugin("plugin-b");
            assertTrue(manager.send("plugin-a", request("a")).isSuccessful());
            assertTrue(manager.send("plugin-b", request("b")).isSuccessful());
        }
    }

    private static SmsRequest request(String id) {
        return SmsRequest.builder().requestId(id).phoneNumbers("13800138000").content("hi").build();
    }

    private static String invoke(Class<?> type, String method) throws Exception {
        Method m = type.getMethod(method);
        return (String) m.invoke(type.getConstructor().newInstance());
    }
}
