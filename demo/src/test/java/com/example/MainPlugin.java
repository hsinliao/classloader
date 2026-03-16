package com.example;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.example.util.Helper;  // 依赖 lib.jar

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class MainPlugin {

    public void execute() throws Exception {
        System.out.println("MainPlugin is running.");
        Path libDir = Paths.get(System.getProperty("user.dir"),"lib","lib.jar").toAbsolutePath();
        List<URL> classpath = new ArrayList<>();
        classpath.add(libDir.toUri().toURL());
        URLClassLoader classLoader = new URLClassLoader(classpath.toArray(new URL[0]),Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(classLoader);
        // 引用 而非反射，
        Helper.help();  // 调用依赖库的方法
        ClassLoader classLoader1 = Helper.class.getClassLoader();
        if (MainPlugin.class.getClassLoader() == classLoader1) {
            // 上述的构建URLClassLoader 无效，包括设置线程上下文类加载器
            System.out.println("引用类的类加载器与当前类是同一个");
        }
    }
}