package com.hsin.sms.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Child-first plugin class loader with a strict parent-first allow-list.
 *
 * <p>Parent-first (delegated to the host application class loader): JDK/platform classes,
 * {@code javax.*}, {@code jdk.*}, {@code sun.*}, the SPI API and the plugin SDK.
 *
 * <p>Child-first: plugin classes and every third-party dependency in {@code lib/}.
 * This is what allows plugin A to use okhttp 3 while plugin B uses okhttp 4.</p>
 *
 * <p>SPI classes are therefore only ever provided by the host. If a plugin bundles
 * a copy, the JVM still links against the host copy and surfaces a
 * {@code ClassCastException}/{@code LinkageError} instead of silently loading a duplicate.</p>
 */
public final class PluginClassLoader extends URLClassLoader {

    private static final String[] PARENT_FIRST_PREFIXES = {
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "com.hsin.sms.spi.", "com.hsin.sms.sdk.",
            "org.w3c.", "org.xml."
    };

    private static final String[] PARENT_FIRST_RESOURCE_PREFIXES = {
            "java/", "javax/", "jdk/", "com/hsin/sms/spi/", "com/hsin/sms/sdk/"
    };

    private final String pluginId;

    static {
        registerAsParallelCapable();
    }

    public PluginClassLoader(String pluginId, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.pluginId = pluginId;
    }

    /** The plugin id this loader was created for. */
    public String pluginId() {
        return pluginId;
    }

    /**
     * Custom delegation: parent-first for JDK/SPI/SDK packages, child-first for
     * plugin classes and bundled third-party dependencies.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (isParentFirst(name)) {
                    loaded = getParent().loadClass(name);
                } else {
                    loaded = findChildFirst(name);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> findChildFirst(String name) throws ClassNotFoundException {
        try {
            return findClass(name);
        } catch (ClassNotFoundException childNotFound) {
            return getParent().loadClass(name);
        }
    }

    private static boolean isParentFirst(String name) {
        for (String prefix : PARENT_FIRST_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resource lookup follows the same delegation rule as class loading so that
     * {@code META-INF/services/...} files are found in the plugin jar first.
     */
    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        if (own != null && !isParentFirstResource(name)) {
            return own;
        }
        URL parentResource = super.getResource(name);
        return parentResource != null ? parentResource : own;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> urls = new LinkedHashSet<>();
        if (!isParentFirstResource(name)) {
            Enumeration<URL> own = findResources(name);
            while (own.hasMoreElements()) {
                urls.add(own.nextElement());
            }
        }
        Enumeration<URL> parentResources = getParent().getResources(name);
        while (parentResources.hasMoreElements()) {
            urls.add(parentResources.nextElement());
        }
        if (isParentFirstResource(name)) {
            Enumeration<URL> own = findResources(name);
            while (own.hasMoreElements()) {
                urls.add(own.nextElement());
            }
        }
        return java.util.Collections.enumeration(urls);
    }

    private static boolean isParentFirstResource(String name) {
        for (String prefix : PARENT_FIRST_RESOURCE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "PluginClassLoader{pluginId='" + pluginId + "', identity="
                + Integer.toHexString(System.identityHashCode(this)) + "}";
    }
}
