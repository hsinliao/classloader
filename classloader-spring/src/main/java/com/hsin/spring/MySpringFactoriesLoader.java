package com.hsin.spring;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 核心特性：按 Key 精准加载、类加载器隔离、空值/重复过滤、自定义规则筛选、健壮的异常处理
 */
public class MySpringFactoriesLoader {

    public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/my-spring.factories";

    // 缓存：Key=接口全类名，Value=实现类全类名列表（按类加载器隔离，避免多环境冲突）
    private static final Map<ClassLoader, Map<String, List<String>>> FACTORIES_CACHE = new ConcurrentHashMap<>();

    private MySpringFactoriesLoader() {}

    // ===================== 核心方法：按 Key 精准加载实现类名 =====================
    /**
     * 按 Key（接口类）精准加载所有实现类全类名（默认使用当前线程上下文类加载器）
     * @param factoryType 接口/抽象类（Key）
     * @return 该 Key 对应的实现类全类名列表（去重、非空）
     */
    public static List<String> loadFactoryNames(Class<?> factoryType) {
        return loadFactoryNames(factoryType, Thread.currentThread().getContextClassLoader());
    }

    /**
     * 重载：指定类加载器，按 Key 精准加载实现类名（适配多类加载器环境）
     * @param factoryType 接口/抽象类（Key）
     * @param classLoader 类加载器（null 则使用系统类加载器）
     * @return 该 Key 对应的实现类全类名列表
     */
    public static List<String> loadFactoryNames(Class<?> factoryType, ClassLoader classLoader) {
        // 1. 确定实际使用的类加载器（隔离不同环境）
        ClassLoader actualClassLoader = (classLoader != null) ? classLoader : ClassLoader.getSystemClassLoader();
        String factoryTypeName = factoryType.getName();

        // 2. 按类加载器隔离缓存，避免跨环境冲突
        return FACTORIES_CACHE.computeIfAbsent(actualClassLoader, cl -> new ConcurrentHashMap<>())
                .computeIfAbsent(factoryTypeName, key -> {
                    try {
                        // 3. 扫描所有配置文件，仅解析当前 Key 对应的实现类
                        Enumeration<URL> urls = actualClassLoader.getResources(FACTORIES_RESOURCE_LOCATION);
                        List<String> result = new ArrayList<>();
                        while (urls.hasMoreElements()) {
                            URL url = urls.nextElement();
                            parseFactoriesFile(url, key, result);
                        }
                        return Collections.unmodifiableList(result); // 不可变列表，防止外部修改
                    } catch (IOException e) {
                        throw new IllegalArgumentException("加载 Key=" + key + " 的工厂配置文件失败", e);
                    }
                });
    }

    // ===================== 扩展方法：按 Key + 自定义规则精准加载实例 =====================
    /**
     * 按 Key 加载并实例化所有实现类
     * @param factoryType 接口/抽象类（Key）
     * @param <T> 泛型限定
     * @return 实现类实例列表
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> loadFactories(Class<T> factoryType) {
        return loadFactories(factoryType, name -> true); // 默认加载所有实现类
    }

    /**
     * 按 Key + 自定义规则精准加载实例（核心优化点）
     * @param factoryType 接口/抽象类（Key）
     * @param filter 自定义筛选规则（如类名包含指定字符、注解匹配等）
     * @param <T> 泛型限定
     * @return 符合规则的实现类实例列表
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> loadFactories(Class<T> factoryType, Predicate<String> filter) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<String> factoryNames = loadFactoryNames(factoryType, classLoader);
        List<T> instances = new ArrayList<>();

        for (String factoryName : factoryNames) {
            // 1. 应用自定义筛选规则，实现精准加载
            if (filter.test(factoryName)) {
                try {
                    // 2. 加载类并实例化（强制初始化，保证类加载完成）
                    Class<?> clazz = Class.forName(factoryName, true, classLoader);
                    // 校验：确保实现类是 Key 接口的子类/实现
                    if (!factoryType.isAssignableFrom(clazz)) {
                        throw new IllegalArgumentException(factoryName + " 不是 " + factoryType.getName() + " 的实现类");
                    }
                    // 3. 无参构造实例化（要求实现类有公有无参构造）
                    T instance = (T) clazz.getDeclaredConstructor().newInstance();
                    instances.add(instance);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("实现类不存在：" + factoryName, e);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(factoryName + " 缺少公有无参构造方法", e);
                } catch (Exception e) {
                    throw new IllegalArgumentException("实例化实现类失败：" + factoryName, e);
                }
            }
        }
        return instances;
    }

    // ===================== 工具方法：加载单个实现类（极致精准） =====================
    /**
     * 按 Key + 实现类全类名 精准加载单个实例（最常用的精准加载场景）
     * @param factoryType 接口/抽象类（Key）
     * @param targetClassName 目标实现类全类名
     * @param <T> 泛型限定
     * @return 目标实现类实例
     */
    public static <T> T loadSingleFactory(Class<T> factoryType, String targetClassName) {
        List<T> factories = loadFactories(factoryType, name -> name.equals(targetClassName));
        if (factories.isEmpty()) {
            throw new IllegalArgumentException("Key=" + factoryType.getName() + " 下未找到实现类：" + targetClassName);
        }
        if (factories.size() > 1) {
            throw new IllegalArgumentException("Key=" + factoryType.getName() + " 下找到多个 " + targetClassName + " 实现类");
        }
        return factories.get(0);
    }

    // ===================== 内部方法：解析配置文件（优化版） =====================
    /**
     * 解析单个配置文件，提取指定 Key 对应的实现类（过滤空值、重复、非法类名）
     */
    private static void parseFactoriesFile(URL url, String factoryTypeName, List<String> result) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            Properties properties = new Properties();
            properties.load(inputStream);

            // 1. 精准匹配 Key，只处理当前 Key 对应的行
            String factoryClassNames = properties.getProperty(factoryTypeName);
            if (factoryClassNames == null) {
                return; // 无匹配 Key，直接返回
            }

            // 2. 拆分实现类名，过滤空值、重复、非法类名
            String[] classNames = factoryClassNames.split(",");
            for (String className : classNames) {
                String trimedName = className.trim();
                // 过滤规则：非空 + 包含包名（避免非法类名） + 未重复
                if (trimedName.contains(".")
                        && !result.contains(trimedName)) {
                    result.add(trimedName);
                }
            }
        }
    }

    // ===================== 辅助方法：缓存管理 =====================
    /**
     * 清空指定类加载器的缓存（测试/动态刷新用）
     */
    public static void clearCache(ClassLoader classLoader) {
        FACTORIES_CACHE.remove(classLoader);
    }

    /**
     * 清空所有缓存
     */
    public static void clearAllCache() {
        FACTORIES_CACHE.clear();
    }

}
