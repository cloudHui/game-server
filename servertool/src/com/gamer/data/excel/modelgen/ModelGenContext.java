package com.gamer.data.excel.modelgen;

import java.util.List;

import com.gamer.data.excel.shared.FileWithSheets;

/**
 * 模型代码生成运行时上下文（日志与路径），与 UI 解耦。
 */
public interface ModelGenContext {

    /** 无界面/未绑定时的兜底上下文（仅输出控制台日志） */
    ModelGenContext NOOP = new ModelGenContext() {
        @Override
        public void logMessage(String message) {
            System.out.println(message);
        }

        @Override
        public String getServerPath() {
            throw new IllegalStateException("ModelGenContext 未绑定 serverPath");
        }

        @Override
        public String getXmlPath() {
            throw new IllegalStateException("ModelGenContext 未绑定 xmlPath");
        }

        @Override
        public void afterConfigFilesWritten(List<FileWithSheets> files) {
        }
    };

    /**
     * 输出日志。
     *
     * @param message 日志内容
     */
    void logMessage(String message);

    /**
     * Server 工程根路径。
     *
     * @return server 路径
     */
    String getServerPath();

    /**
     * Excel 配置表目录路径。
     *
     * @return xml/excel 目录路径
     */
    String getXmlPath();

    /**
     * Config 类文件写入完成后回调（在 Manager 弹框之前），用于同步写入 limit 等。
     *
     * @param files
     *            本次生成的 Excel 子集
     */
    void afterConfigFilesWritten(List<FileWithSheets> files);
}
