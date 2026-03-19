# Java ClassLoader Demo

一个用于演示和学习 Java 类加载机制的示例工程。项目通过多个可运行示例展示类加载的核心流程，包括类加载顺序、双亲委派模型、自定义 ClassLoader、动态加载类以及类隔离等常见场景。

## 适合用于：

- 理解 JVM 类加载原理
- 调试类冲突、依赖冲突问题
- 学习插件化、模块隔离、热加载的基础实现思路
- 作为排查 `ClassNotFoundException`、`NoClassDefFoundError`、`ClassCastException` 等问题的辅助示例

## 项目目标

Java 类加载机制是 JVM 运行时的重要基础，也是很多框架、容器、插件系统和中间件设计的核心。  
本项目希望通过一组清晰、可运行、可观察输出结果的示例，帮助从“概念理解”走到“实际验证”。

通过这些示例，可以理解：

- 类从加载到初始化的大致过程
- 不同类加载器之间的层次关系
- 双亲委派模型的工作方式
- 为什么“同一个类”在不同 ClassLoader 下会被视为不同类型
- 自定义类加载器如何实现类的动态加载
- 类隔离在插件化、容器化中的意义
- 打破双亲委派时可能带来的影响和风险

## 示例内容

项目包含以下几类演示示例

### 双亲委派模型演示

用于说明 Java 默认类加载机制中的双亲委派流程：

- Bootstrap ClassLoader
- Platform ClassLoader（JDK 9+） / ExtClassLoader（JDK 8）
- AppClassLoader

帮助理解：

- 一个类加载请求是如何逐级向上委托的
- 为什么 JDK 核心类不会被轻易篡改
- 为什么业务类默认由应用类加载器加载

### 自定义 ClassLoader

通过继承 `ClassLoader` 并重写 `findClass()`，演示如何从自定义路径加载 `.class` 文件。

适合学习：

- `loadClass()` 与 `findClass()` 的职责区别
- `defineClass()` 的作用
- 如何实现一个最基础的自定义类加载器
- 如何打印类加载来源，观察类是被哪个加载器加载的

### 类隔离演示

这是理解插件化和模块化最关键的一部分之一。

示例会展示：

- 相同全限定类名的类，由不同 ClassLoader 加载后互不相同
- 为什么会出现：

  `ClassCastException: xxx cannot be cast to xxx`

- 不同模块之间如何通过“接口下沉”或“父加载器共享 API”来解决通信问题

适合用于理解：

- 插件系统
- 应用服务器
- OSGi
- 热加载框架
- 多版本依赖共存