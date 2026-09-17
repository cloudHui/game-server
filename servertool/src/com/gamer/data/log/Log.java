package com.gamer.data.log;

/** 通用文本日志接口。 */
public interface Log {

    void logMessage(String message);

    void logMessage(String message, boolean redShow);
}
