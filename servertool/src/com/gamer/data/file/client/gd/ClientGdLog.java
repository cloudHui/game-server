package com.gamer.data.file.client.gd;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.gamer.data.log.Log;
import com.gamer.data.file.utils.Const;
import com.gamer.data.file.utils.Utils;
import com.gamer.data.file.zip.ZipArchiveModule;

/**
 * 步骤日志适配。
 */
public final class ClientGdLog {

    private ClientGdLog() {}

    /** 步骤日志（不含耗时）。 */
    public interface Ui {
        void line(String line);

        void status(String text);
    }

    /**
     * @param log UI 日志
     * @return 去掉耗时后缀的生成进度日志
     */
    public static Log progressLog(final Ui log) {
        return new Log() {
            @Override
            public void logMessage(String message) {
                log.line(stripElapsed(message));
            }

            @Override
            public void logMessage(String message, boolean redShow) {
                log.line(stripElapsed(message));
            }
        };
    }

    /**
     * @param log UI 日志
     * @param throttle 节流，可为 null
     * @return zip 监听
     */
    public static ZipArchiveModule.Listener zipListener(final Ui log,
        final ZipArchiveModule.ProgressThrottle throttle) {
        return new ZipArchiveModule.Listener() {
            @Override
            public void log(String line) {
                log.line(line);
            }

            @Override
            public void progress(String action, int current, int total) {
                if (throttle == null || throttle.shouldUpdate(current, total)) {
                    log.status(action + " " + current + "/" + total);
                }
            }
        };
    }

    /**
     * @param message 原文
     * @return 去掉「耗时」后的文案
     */
    public static String stripElapsed(String message) {
        if (message == null) {
            return "";
        }
        int idx = message.indexOf("，耗时");
        if (idx < 0) {
            idx = message.indexOf("，总耗时");
        }
        if (idx < 0) {
            idx = message.indexOf(" 总耗时");
        }
        return idx >= 0 ? message.substring(0, idx) : message;
    }

    /**
     * 只列目录下一层 .gd。dir 无效返回空列表；listFiles 失败返回 null。
     *
     * @param dir 目录
     * @return gd 列表
     */
    static List<File> listGd(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return new ArrayList<>();
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        List<File> gds = new ArrayList<>();
        for (File child : children) {
            if (child.isFile() && Const.GD.equalsIgnoreCase(Utils.getFileExtension(child.getName()))) {
                gds.add(child);
            }
        }
        return gds;
    }
}
