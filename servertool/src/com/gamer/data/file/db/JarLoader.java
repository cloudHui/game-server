package com.gamer.data.file.db;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 懒加载 mysql / proto 外挂 jar，并在 jar 变更时重建 ClassLoader。
 */
public final class JarLoader {

    /** mysql 驱动 ClassLoader */
    private URLClassLoader mysqlLoader;

    /** proto ClassLoader（common-proto + protobuf-java） */
    private URLClassLoader protoLoader;

    /** mysql jar 上次修改时间 */
    private long mysqlJarModified;

    /** common-proto jar 上次修改时间 */
    private long protoJarModified;

    /** protobuf-java jar 上次修改时间 */
    private long protobufJavaJarModified;

    /** 当前 mysql jar 路径 */
    private String mysqlJarPath;

    /** 当前 common-proto jar 路径 */
    private String protoJarPath;

    /** 当前 protobuf-java jar 路径 */
    private String protobufJavaJarPath;

    /**
     * 获取 mysql ClassLoader，路径变化或 jar 更新时自动重建。
     *
     * @param jarPath
     *            mysql jar 绝对路径
     * @return ClassLoader
     * @throws Exception
     *             jar 不存在或加载失败
     */
    public synchronized ClassLoader getMysqlLoader(String jarPath) throws Exception {
        URLClassLoader loader = ensureSingleJarLoader(jarPath, mysqlJarPath, mysqlLoader, mysqlJarModified);
        mysqlJarPath = jarPath;
        mysqlLoader = loader;
        mysqlJarModified = jarLastModified(jarPath);
        return mysqlLoader;
    }

    /**
     * 获取 proto ClassLoader（common-proto.jar + protobuf-java.jar）。
     *
     * @param protoJarPath
     *            common-proto.jar 路径
     * @param protobufJavaJarPath
     *            protobuf-java.jar 路径
     * @return ClassLoader
     * @throws Exception
     *             jar 不存在或加载失败
     */
    public synchronized ClassLoader getProtoLoader(String protoJarPath, String protobufJavaJarPath) throws Exception {
        File protoJar = requireJarFile(protoJarPath, "common-proto.jar");
        File protobufJavaJar = requireJarFile(protobufJavaJarPath, "protobuf-java.jar");
        long protoMod = protoJar.lastModified();
        long libMod = protobufJavaJar.lastModified();
        if (protoLoader != null && protoJarPath.equals(this.protoJarPath)
            && protobufJavaJarPath.equals(this.protobufJavaJarPath) && protoMod == protoJarModified
            && libMod == protobufJavaJarModified) {
            return protoLoader;
        }
        closeQuietly(protoLoader);
        URL[] urls = new URL[] {protoJar.toURI().toURL(), protobufJavaJar.toURI().toURL()};
        protoLoader = new URLClassLoader(urls, JarLoader.class.getClassLoader());
        this.protoJarPath = protoJarPath;
        this.protobufJavaJarPath = protobufJavaJarPath;
        protoJarModified = protoMod;
        protobufJavaJarModified = libMod;
        return protoLoader;
    }

    /**
     * 强制丢弃已加载的 ClassLoader（供「重载 proto jar」按钮使用）。
     */
    public synchronized void invalidateAll() {
        closeQuietly(mysqlLoader);
        closeQuietly(protoLoader);
        mysqlLoader = null;
        protoLoader = null;
        mysqlJarModified = 0;
        protoJarModified = 0;
        protobufJavaJarModified = 0;
    }

    /**
     * 单 jar ClassLoader。
     */
    private URLClassLoader ensureSingleJarLoader(String jarPath, String cachedPath, URLClassLoader loader,
        long cachedModified) throws Exception {
        File jarFile = requireJarFile(jarPath, "jar");
        long modified = jarFile.lastModified();
        if (loader != null && jarPath.equals(cachedPath) && modified == cachedModified) {
            return loader;
        }
        closeQuietly(loader);
        return new URLClassLoader(new URL[] {jarFile.toURI().toURL()}, JarLoader.class.getClassLoader());
    }

    /**
     * 校验 jar 文件存在。
     */
    private File requireJarFile(String jarPath, String label) {
        File jarFile = new File(jarPath);
        if (!jarFile.isFile()) {
            throw new IllegalStateException(label + " 不存在: " + jarPath);
        }
        return jarFile;
    }

    /**
     * @param jarPath
     *            jar 路径
     * @return 最后修改时间
     */
    private long jarLastModified(String jarPath) {
        File file = new File(jarPath);
        return file.isFile() ? file.lastModified() : 0L;
    }

    /**
     * 关闭 ClassLoader，忽略异常。
     */
    private void closeQuietly(URLClassLoader loader) {
        if (loader != null) {
            try {
                loader.close();
            } catch (Exception ignored) {
                // 关闭失败不影响后续重建
            }
        }
    }
}
