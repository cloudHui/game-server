package com.gamer.data.mpcserver.core;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 按父类型和注解扫描实现类，目录与 JAR 使用同一套规则。 */
public final class ClassScanner {

    private ClassScanner() {}

    /**
     * 扫描父类型所在包及子包中带指定注解的具体实现类，不触发类初始化。
     *
     * @param baseType 处理器父类或接口，同时确定扫描包和类加载器
     * @param annotationType 处理器注解
     * @return 按类名排序、去重后的实现类
     */
    public static <T> List<Class<? extends T>> scan(Class<T> baseType,
            Class<? extends Annotation> annotationType) {
        ClassLoader loader = loader(baseType);
        String pkg = baseType.getPackage().getName();
        String path = pkg.replace('.', '/');
        Set<String> names = new TreeSet<>();
        try {
            Enumeration<URL> urls = loader.getResources(path);
            if (!urls.hasMoreElements()) {
                throw new IllegalStateException("处理器扫描失败: 找不到包 " + pkg);
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    collectDir(new File(url.toURI()), pkg, names);
                } else if ("jar".equals(protocol)) {
                    collectJar(url, path, names);
                } else {
                    throw new IllegalStateException("未识别的协议: " + protocol + ", url=" + url);
                }
            }
            List<Class<? extends T>> classes = new ArrayList<>();
            for (String name : names) {
                Class<?> candidate = Class.forName(name, false, loader);
                if (candidate != baseType && baseType.isAssignableFrom(candidate)
                        && !candidate.isInterface() && !Modifier.isAbstract(candidate.getModifiers())
                        && candidate.getAnnotation(annotationType) != null) {
                    classes.add(candidate.asSubclass(baseType));
                }
            }
            return classes;
        } catch (IOException | URISyntaxException | ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("处理器扫描失败: base=" + baseType.getName()
                + ", annotation=" + annotationType.getName(), e);
        }
    }

    private static ClassLoader loader(Class<?> type) {
        if (type.getClassLoader() != null) {
            return type.getClassLoader();
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : ClassScanner.class.getClassLoader();
    }

    private static void collectDir(File dir, String pkg, Set<String> names) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectDir(file, pkg + "." + file.getName(), names);
                continue;
            }
            String name = file.getName();
            if (name.endsWith(".class") && !name.contains("$")) {
                names.add(pkg + "." + name.substring(0, name.length() - 6));
            }
        }
    }

    private static void collectJar(URL url, String path, Set<String> names) throws IOException {
        JarFile jar = ((JarURLConnection)url.openConnection()).getJarFile();
        String prefix = path.endsWith("/") ? path : path + "/";
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.endsWith(".class") || name.contains("$") || !name.startsWith(prefix)) {
                continue;
            }
            names.add(name.substring(0, name.length() - 6).replace('/', '.'));
        }
    }
}
