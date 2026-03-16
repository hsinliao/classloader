
javac com/example/util/Helper.java
jar cf lib.jar com/example/util/Helper.class


编译时需要将 lib.jar 添加到 classpath，但打包时不要将 Helper 类打入 app.jar，仅保留对它的引用。打包命令：

javac -cp lib.jar com/example/MainPlugin.java
jar cf app.jar com/example/MainPlugin.class

