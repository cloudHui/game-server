package com.gamer.data.process;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** 文本子进程执行选项。 */
public final class ProcessOptions {

    File workDirectory;
    Map<String, String> environment = Collections.emptyMap();
    Charset charset = StandardCharsets.UTF_8;
    boolean nullInput;
    /** 非空时 stdin 来自该文件，优先于 nullInput。 */
    File inputFile;
    boolean discardError;
    long timeoutMillis;

    /** @param value 工作目录，可空 @return 当前选项 */
    public ProcessOptions workDirectory(File value) {
        workDirectory = value;
        return this;
    }

    /** @param value 环境变量，可空 @return 当前选项 */
    public ProcessOptions environment(Map<String, String> value) {
        environment = value == null ? Collections.emptyMap() : new HashMap<>(value);
        return this;
    }

    /** @param value 输出编码 @return 当前选项 */
    public ProcessOptions charset(Charset value) {
        charset = value == null ? StandardCharsets.UTF_8 : value;
        return this;
    }

    /** @param value true 表示 stdin 接空设备 @return 当前选项 */
    public ProcessOptions nullInput(boolean value) {
        nullInput = value;
        return this;
    }

    /** @param value stdin 文件；非空时优先于 nullInput @return 当前选项 */
    public ProcessOptions inputFile(File value) {
        inputFile = value;
        return this;
    }

    /** @param value true 表示丢弃 stderr @return 当前选项 */
    public ProcessOptions discardError(boolean value) {
        discardError = value;
        return this;
    }

    /** @param value 超时毫秒；小于等于 0 表示不限制 @return 当前选项 */
    public ProcessOptions timeoutMillis(long value) {
        timeoutMillis = value;
        return this;
    }
}
