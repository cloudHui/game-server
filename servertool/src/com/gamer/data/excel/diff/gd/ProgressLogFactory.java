package com.gamer.data.excel.diff.gd;

import com.gamer.data.log.Log;
import com.gamer.data.gdg.progress.GdMultiLineProgressLog;

/**
 * 为后台 GD 任务创建支持进度块刷新的 {@link Log} 包装。
 */
public class ProgressLogFactory {

    /**
     * 进度块日志写入口。
     */
    public interface LineLogSink {
        /**
         * @param message
         *            消息
         * @param red
         *            是否红色
         */
        void logMessage(String message, boolean red);

        /**
         * @param blockText
         *            进度块全文
         * @param syncOnEdt
         *            是否同步等待 EDT
         */
        void rewriteProgressBlock(String blockText, boolean syncOnEdt);
    }

    private ProgressLogFactory() {}

    /**
     * 创建 {@link GdMultiLineProgressLog}，进度块与带时间戳日志均经 sink 转到 EDT。
     *
     * @param sink
     *            日志回调
     * @return 可供 {@link com.gamer.data.gdg.progress.GdProgressContext} 使用的 Log
     */
    public static Log createEdtSafeMultiLineLog(final LineLogSink sink) {
        GdMultiLineProgressLog.GdLineAwareLog aware = new GdMultiLineProgressLog.GdLineAwareLog() {
            @Override
            public void logMessage(String message) {
                sink.logMessage(message, false);
            }

            @Override
            public void logMessage(String message, boolean redShow) {
                sink.logMessage(message, redShow);
            }

            @Override
            public void rewriteProgressBlock(String blockText, boolean syncOnEdt) {
                sink.rewriteProgressBlock(blockText, syncOnEdt);
            }
        };
        return new GdMultiLineProgressLog(aware);
    }
}
