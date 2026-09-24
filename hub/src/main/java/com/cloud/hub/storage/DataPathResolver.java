package com.cloud.hub.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 数据文件路径解析器。
 * <p>
 * 统一根据配置的根目录（默认为系统工作目录 {@code user.dir}）解析相对路径或绝对路径，
 * 避免不同子系统运行路径不一致引发的文件找不到异常。
 * </p>
 *
 * @author cloud
 */
@Component
public class DataPathResolver {

    /** 规范化后的系统数据根路径 */
    private final Path root;

    /**
     * 构造解析器，默认使用应用工作目录作为基础根。
     *
     * @param root 外部注入的根路径字符串
     */
    public DataPathResolver(@Value("${hub.root:${user.dir}}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    /**
     * 将给定的相对路径或绝对路径解析为统一规范的绝对路径。
     *
     * @param configuredPath 配置的文件路径
     * @return 最终规范化的 Path 路径对象
     */
    public Path resolve(String configuredPath) {
        Path path = Paths.get(configuredPath);
        return (path.isAbsolute() ? path : root.resolve(path)).normalize();
    }

    /**
     * 获取系统根目录路径。
     *
     * @return 根路径对象
     */
    public Path root() {
        return root;
    }
}

