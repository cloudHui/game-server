package com.gamer.data.mpcserver.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按 @Process 构建命令注册表；失败时不发布部分结果。 */
public final class HandlerRegistry {

    private HandlerRegistry() {}

    /**
     * 构建单处理器表。不同实现类占用同一命令名时失败。
     *
     * @param baseType 处理器父类型及扫描包锚点
     * @return 命令名到处理器的完整注册表
     */
    public static <T> Map<String, T> buildSingle(Class<T> baseType) {
        Map<String, Class<? extends T>> bindings = new LinkedHashMap<>();
        for (Class<? extends T> type : ClassScanner.scan(baseType, Process.class)) {
            String key = type.getAnnotation(Process.class).value();
            if (key.isEmpty()) {
                throw new IllegalStateException("MCP命令未声明名称: " + type.getName());
            }
            Class<? extends T> old = bindings.putIfAbsent(key, type);
            if (old != null && old != type) {
                throw new IllegalStateException(
                    "MCP命令重复: " + key + ", old=" + old.getName() + ", new=" + type.getName());
            }
        }
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, Class<? extends T>> entry : bindings.entrySet()) {
            result.put(entry.getKey(), newInstance(entry.getValue()));
        }
        return result;
    }

    private static <T> T newInstance(Class<? extends T> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("MCP命令实例化失败: " + type.getName(), e);
        }
    }
}
