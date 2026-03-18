package com.hsin.spring;

import com.hsin.spi.Logger;

import java.util.List;

public class TestOptimizedLoader {

    public static void main(String[] args) {
        // ========== 测试 1：按 Key 加载所有实现类（基础精准） ==========
        System.out.println("===== 按 Key 加载所有实现类 =====");
        List<Logger> allLogServices = MySpringFactoriesLoader.loadFactories(Logger.class);
        for (Logger logger : allLogServices) {
            logger.log("Key 精准加载所有实现类");
        }

        // ========== 测试 2：按 Key + 自定义规则加载（筛选类名含 File 的实现类） ==========
        System.out.println("\n===== 按自定义规则精准加载 =====");
        List<Logger> fileLogServices = MySpringFactoriesLoader.loadFactories(
                Logger.class,
                className -> className.contains("File") // 自定义筛选规则
        );
        for (Logger logger : fileLogServices) {
            logger.log("自定义规则（类名含File）精准加载");
        }

        // ========== 测试 3：按 Key + 实现类名加载单个实例（极致精准） ==========
        System.out.println("\n===== 按实现类名精准加载单个实例 =====");
        Logger consoleLog = MySpringFactoriesLoader.loadSingleFactory(
                Logger.class, "com.hsin.provider.ConsoleLogger"
        );
        consoleLog.log("单个实现类精准加载");
    }
}
