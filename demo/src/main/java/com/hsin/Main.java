package com.hsin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws Exception {
        Path appDir = Paths.get(System.getProperty("user.dir"), "app").toAbsolutePath();
        Path libDir = Paths.get(System.getProperty("user.dir"), "lib").toAbsolutePath();
        List<URL> classpath = new ArrayList<>();
        collectJars(appDir,classpath);
        // 加上下面这一行，能找到类
        // collectJars(libDir,classpath);
        URLClassLoader classLoader = new URLClassLoader(classpath.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
        Class<?> clazz = classLoader.loadClass("com.example.MainPlugin");

        Object instance = clazz.getDeclaredConstructor().newInstance();
        clazz.getMethod("execute").invoke(instance);
    }

    private static void collectJars(Path jarDir, List<URL> classpath) throws MalformedURLException {
        // 收集所有 JAR 文件的 URL
        File dir = jarDir.toFile();
        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            System.out.println("No JAR files found in " + jarDir);
            return;
        }

        for (int i = 0; i < jars.length; i++) {
            classpath.add(jars[i].toURI().toURL());
        }
    }
}