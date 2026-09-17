package com.gamer.data.file.cmd;

import java.io.File;

/** 命令任务数据，集中保存调度状态、输出和失败摘要。 */
final class CmdTask {
    /** 任务编号。 */
    final long id;
    /** 工作目录。 */
    final File workDir;
    /** 工作目录绝对路径。 */
    final String workDirPath;
    /** 执行命令。 */
    final String command;
    /** 日志键（目录+命令）。 */
    final String logKey;
    /** 是否属于一键执行的协议任务。 */
    final boolean protocolBatchTask;
    /** 输出缓存。 */
    private final StringBuilder outputBuilder = new StringBuilder();
    /** 开始时间。 */
    long startMillis;
    /** 结束时间。 */
    long endMillis;
    /** 退出码。 */
    int exitCode = -1;
    /** 异常信息。 */
    String errorMessage = "";
    /** 关联日志视图。 */
    TaskLogView logView;

    /**
     * 创建待调度命令任务。
     *
     * @param id 任务编号
     * @param workDir 工作目录
     * @param command 执行命令
     * @param logKey 日志键
     */
    CmdTask(long id, File workDir, String command, String logKey, boolean protocolBatchTask) {
        this.id = id;
        this.workDir = workDir;
        this.workDirPath = workDir.getAbsolutePath();
        this.command = command;
        this.logKey = logKey;
        this.protocolBatchTask = protocolBatchTask;
    }

    /** @param text 待追加输出 */
    synchronized void appendOutput(String text) {
        outputBuilder.append(text);
    }

    /** @return 完整任务输出 */
    synchronized String getOutput() {
        return outputBuilder.toString();
    }
}
