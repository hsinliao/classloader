package com.hsin;

import com.hsin.spi.Logger;

import java.util.ServiceLoader;

public class LoggerClient {

    public static void main(String[] args) {
        // 使用 ServiceLoader 加载所有注册的 Logger 实现
        ServiceLoader<Logger> serviceLoader = ServiceLoader.load(Logger.class);

        System.out.println("加载到的所有 Logger 实现：");
        for (Logger logger : serviceLoader) {
            logger.log("Hello SPI!");   // 每个实现都会执行
        }

        // 演示如何只获取第一个（如果有顺序需求，可借助迭代器）
        ServiceLoader<Logger> loader = ServiceLoader.load(Logger.class);
        Logger first = loader.iterator().next();
        System.out.println("\n只调用第一个实现：");
        first.log("Only first logger");
    }
}
