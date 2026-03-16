package com.hsin;

public class ClassLoaderDemo {

    public static void main(String[] args) {

        ClassLoader appClassLoader = ClassLoaderDemo.class.getClassLoader();
        System.out.println("AppClassLoader: " + appClassLoader);

        ClassLoader extClassLoader = appClassLoader.getParent();
        System.out.println("ExtClassLoader: " + extClassLoader);

        ClassLoader bootstrap = extClassLoader.getParent();
        System.out.println("BootstrapClassLoader: " + bootstrap);
    }
}
