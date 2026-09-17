package com.gamer.data.excel.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/**
 * 使用系统默认程序打开文件的通用工具。
 */
public final class ViewDesktopUtil {

    /**
     * 打开失败时的日志回调。
     */
    public interface OpenErrorCallback {
        /**
         * @param message
         *            错误信息
         * @param red
         *            是否红色强调
         */
        void onError(String message, boolean red);
    }

    private ViewDesktopUtil() {
    }

    /**
     * 用 Desktop 打开文件。
     *
     * @param file
     *            待打开文件
     * @param errorCallback
     *            失败回调，可为 null
     */
    public static void openWithDesktop(File file, OpenErrorCallback errorCallback) {
        if (file == null) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                notifyError(errorCallback, "系统不支持 Desktop 打开: " + file.getName());
                return;
            }
            Desktop.getDesktop().open(file);
        } catch (IOException ex) {
            notifyError(errorCallback, "无法打开文件: " + file.getName() + " - " + ex.getMessage());
        }
    }

    /**
     * 通知打开失败。
     *
     * @param errorCallback 回调
     * @param message       消息
     */
    private static void notifyError(OpenErrorCallback errorCallback, String message) {
        if (errorCallback != null) {
            errorCallback.onError(message, true);
        }
    }
}
