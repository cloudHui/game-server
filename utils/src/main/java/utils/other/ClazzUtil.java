package utils.other;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 类反射与包扫描工具类
 * <p>支持扫描文件目录与 Jar 包下的 Class 文件，支持子类查找与包路径过滤。</p>
 *
 * @author cloud
 */
public class ClazzUtil {

    private ClazzUtil() {
    }

    /**
     * 获取指定类的所有子类或实现类（带排除过滤）
     *
     * @param cls    目标接口或父类
     * @param except 排除的包名，支持逗号分隔
     * @return 满足条件的子类列表
     */
    public static List<Class<?>> getAllAssignedClass(Class<?> cls, String except) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> c : getClasses(cls, except)) {
            if (cls.isAssignableFrom(c) && !cls.equals(c)) {
                classes.add(c);
            }
        }
        return classes;
    }

    /**
     * 获取指定类的所有子类或实现类（不过滤）
     */
    public static List<Class<?>> getAllAssignedClass(Class<?> cls) throws Exception {
        return getAllAssignedClass(cls, "");
    }

    /**
     * 获取指定类同包下的所有其它类（带排除过滤）
     */
    public static List<Class<?>> getAllClassExceptPackageClass(Class<?> packageClass, String except) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> c : getClasses(packageClass, except)) {
            if (!packageClass.equals(c)) {
                classes.add(c);
            }
        }
        return classes;
    }

    /**
     * 获取指定类同包下的所有其它类（不过滤）
     */
    public static List<Class<?>> getAllClassExceptPackageClass(Class<?> packageClass) throws Exception {
        return getAllClassExceptPackageClass(packageClass, "");
    }

    /**
     * 读取指定类所在包下的所有类
     */
    public static List<Class<?>> getClasses(Class<?> packageClass, String except) throws Exception {
        return getClasses(packageClass.getPackage().getName(), packageClass, except);
    }

    /**
     * 读取指定类所在包下的所有类（不过滤）
     */
    public static List<Class<?>> getClasses(Class<?> packageClass) throws Exception {
        return getClasses(packageClass, "");
    }

    /**
     * 读取指定包路径下的所有类
     */
    public static List<Class<?>> getClasses(String pk) throws Exception {
        return getClasses(pk, null, "");
    }

    /**
     * 读取指定包路径下的所有类（指定 ClassLoader 锚点类）
     */
    public static List<Class<?>> getClasses(String pk, Class<?> cls) throws Exception {
        return getClasses(pk, cls, "");
    }

    /**
     * 核心扫描入口：根据包名与过滤条件加载所有 Class
     */
    public static List<Class<?>> getClasses(String pk, Class<?> cls, String except) throws Exception {
        String path = pk.replace('.', '/');
        ClassLoader classloader = (cls != null) ? cls.getClassLoader() : Thread.currentThread().getContextClassLoader();
        URL url = classloader.getResource(path);
        if (url == null) {
            throw new Exception("URL get error: " + path);
        }
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            return getClasses(new File(url.getFile()), pk, except);
        } else if ("jar".equals(protocol)) {
            JarFile jarFile = ((JarURLConnection) url.openConnection()).getJarFile();
            return getClassesFromJarFile(jarFile, except, pk);
        }
        throw new Exception("未识别的文件协议: " + protocol);
    }

    /**
     * 从 Jar 文件中扫描所有符合包含与排除规则的 Class
     */
    public static List<Class<?>> getClassesFromJarFile(JarFile jarFile, String except, String include) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class")) {
                name = name.substring(0, name.length() - 6).replace('/', '.');
                if (include != null && !include.isEmpty() && !name.contains(include)) {
                    continue;
                }
                if (needExceptPackage(except, name)) {
                    continue;
                }
                classes.add(Class.forName(name, false, cl));
            }
        }
        return classes;
    }

    public static List<Class<?>> getClassesFromJarFile(JarFile jarFile, String include) throws Exception {
        return getClassesFromJarFile(jarFile, "", include);
    }

    /**
     * 从本地目录递归扫描 Class 文件
     */
    public static List<Class<?>> getClasses(File dir, String pk, String except) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (dir == null || !dir.exists()) {
            return classes;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return classes;
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (File f : files) {
            if (f.isDirectory() && !needExceptFile(except, f.getName())) {
                classes.addAll(getClasses(f, pk + "." + f.getName(), except));
            } else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String className = pk + "." + f.getName().substring(0, f.getName().length() - 6);
                classes.add(Class.forName(className, false, cl));
            }
        }
        return classes;
    }

    public static List<Class<?>> getClasses(File dir, String pk) throws ClassNotFoundException {
        return getClasses(dir, pk, "");
    }

    private static boolean needExceptFile(String except, String name) {
        if (except == null || except.isEmpty()) {
            return false;
        }
        for (String value : except.split(",")) {
            if (name.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean needExceptPackage(String except, String name) {
        if (except == null || except.isEmpty()) {
            return false;
        }
        String[] splitExcept = except.split(",");
        String[] nameSplit = name.split("\\.");
        for (String exp : splitExcept) {
            String trimmed = exp.trim();
            for (String sub : nameSplit) {
                if (trimmed.equals(sub)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 反射扫描当前 ClassLoader 中已加载的目标接口或类实现
     */
    @SuppressWarnings("unchecked")
    public static List<Class<?>> getAllActionSubClass(String classPackageAndName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> cls = Class.forName(classPackageAndName, false, classLoader);
        Class<?> current = classLoader.getClass();
        while (current != ClassLoader.class && current != null) {
            current = current.getSuperclass();
        }
        if (current == null) {
            return Collections.emptyList();
        }
        Field field = current.getDeclaredField("classes");
        field.setAccessible(true);
        Vector<Class<?>> vector = (Vector<Class<?>>) field.get(classLoader);
        List<Class<?>> allSubclass = new ArrayList<>();
        for (Class<?> c : vector) {
            if (cls.isAssignableFrom(c) && !cls.equals(c)) {
                allSubclass.add(c);
            }
        }
        return allSubclass;
    }

    /**
     * 从 Jar 包中读取指定名称的文本文件内容
     */
    public static Map<String, String> getTxtFilesWithContentFromJarFile(JarFile jarFile, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".txt")) {
                if (fileName != null && !fileName.isEmpty() && !name.equals(fileName) && !name.endsWith("/" + fileName)) {
                    continue;
                }
                try (InputStream inputStream = jarFile.getInputStream(entry);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                        sb.setLength(sb.length() - 1);
                    }
                    result.put(name, sb.toString());
                }
            }
        }
        return result;
    }
}