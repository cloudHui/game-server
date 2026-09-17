package com.gamer.data.file.db;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过外挂 proto jar 反射解析 BLOB 为 proto 文本。
 */
public final class ProtoBlobDecoder {

    /** parseFrom 方法缓存：消息名 → Method */
    private final Map<String, Method> parseMethods = new ConcurrentHashMap<>();

    /** TextFormat.printToString(MessageOrBuilder) */
    private Method printToStringMethod;

    /** TextFormat.shortDebugString(MessageOrBuilder) */
    private Method shortDebugStringMethod;

    /** MessageOrBuilder 类型 */
    private Class<?> messageOrBuilderClass;

    /** 当前 proto ClassLoader */
    private ClassLoader protoLoader;

    /**
     * 绑定 proto ClassLoader；loader 变更时清空缓存。
     *
     * @param loader
     *            proto ClassLoader
     */
    public synchronized void bindClassLoader(ClassLoader loader) {
        if (loader == protoLoader) {
            return;
        }
        protoLoader = loader;
        parseMethods.clear();
        printToStringMethod = null;
        shortDebugStringMethod = null;
        messageOrBuilderClass = null;
    }

    /**
     * 将 BLOB 按 proto 消息名解析为文本。
     *
     * @param protoMsg
     *            消息名，如 PBTask
     * @param data
     *            BLOB 字节
     * @return proto 文本
     * @throws Exception
     *             解析失败
     */
    public String decode(String protoMsg, byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            return "";
        }
        if (protoLoader == null) {
            throw new IllegalStateException("proto ClassLoader 未就绪");
        }
        Method parseFrom = resolveParseMethod(protoMsg);
        try {
            Object message = parseFrom.invoke(null, (Object) data);
            return formatMessage(message);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    /**
     * 将 proto 消息格式化为可读文本。
     *
     * @param message
     *            parseFrom 结果
     * @return proto 文本
     * @throws Exception
     *             格式化失败
     */
    private String formatMessage(Object message) throws Exception {
        ensureMessageOrBuilder(message);
        Object typedMessage = messageOrBuilderClass.cast(message);
        try {
            return (String) resolvePrintToStringMethod().invoke(null, typedMessage);
        } catch (Exception ignored) {
            // printToString 失败时尝试 shortDebugString
        }
        try {
            return (String) resolveShortDebugStringMethod().invoke(null, typedMessage);
        } catch (Exception ignored) {
            // shortDebugString 失败时使用 Java toString
        }
        return String.valueOf(message);
    }

    /**
     * 校验并缓存 MessageOrBuilder 类型。
     */
    private void ensureMessageOrBuilder(Object message) throws Exception {
        if (messageOrBuilderClass == null) {
            messageOrBuilderClass =
                Class.forName("com.google.protobuf.MessageOrBuilder", true, protoLoader);
        }
        if (!messageOrBuilderClass.isInstance(message)) {
            throw new IllegalStateException("解析结果不是 MessageOrBuilder: " + message.getClass().getName());
        }
    }

    /**
     * 解析 parseFrom(byte[]) 方法。
     */
    private Method resolveParseMethod(String protoMsg) throws Exception {
        Method cached = parseMethods.get(protoMsg);
        if (cached != null) {
            return cached;
        }
        String className = "com.gow.common.net.proto.LevelProto$" + protoMsg;
        Class<?> protoClass = Class.forName(className, true, protoLoader);
        Method parseFrom = protoClass.getMethod("parseFrom", byte[].class);
        parseMethods.put(protoMsg, parseFrom);
        return parseFrom;
    }

    /**
     * 解析 TextFormat.printToString(MessageOrBuilder)。
     */
    private Method resolvePrintToStringMethod() throws Exception {
        if (printToStringMethod != null) {
            return printToStringMethod;
        }
        Class<?> textFormatClass = Class.forName("com.google.protobuf.TextFormat", true, protoLoader);
        ensureMessageOrBuilderClassLoaded();
        printToStringMethod = textFormatClass.getMethod("printToString", messageOrBuilderClass);
        return printToStringMethod;
    }

    /**
     * 解析 TextFormat.shortDebugString(MessageOrBuilder)。
     */
    private Method resolveShortDebugStringMethod() throws Exception {
        if (shortDebugStringMethod != null) {
            return shortDebugStringMethod;
        }
        Class<?> textFormatClass = Class.forName("com.google.protobuf.TextFormat", true, protoLoader);
        ensureMessageOrBuilderClassLoaded();
        shortDebugStringMethod = textFormatClass.getMethod("shortDebugString", messageOrBuilderClass);
        return shortDebugStringMethod;
    }

    /**
     * 加载 MessageOrBuilder 类型。
     */
    private void ensureMessageOrBuilderClassLoaded() throws Exception {
        if (messageOrBuilderClass == null) {
            messageOrBuilderClass =
                Class.forName("com.google.protobuf.MessageOrBuilder", true, protoLoader);
        }
    }
}
