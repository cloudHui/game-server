package com.gamer.data.file.client.gd;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gamer.data.excel.diff.util.SvnChangedXlsxPuller;
import com.gamer.data.excel.diff.util.SvnChangedXlsxPuller.ChangedXlsx;
import com.gamer.data.file.config.PathConfig;
import com.gamer.data.file.zip.ZipArchiveModule;
import com.gamer.data.gdg.generate.XlsxGdParallelProcessor;
import com.gamer.data.gdg.progress.GdProgressContext;
import com.gamer.data.gdg.util.GdBatchResult;

/**
 * 客户端使用 GD：SVN 差异、生成到 GD/data、整包覆盖 zip。
 */
public final class ClientGd {

    private ClientGd() {}

    /**
     * svn status 收集变更 xlsx，再生成到 GD/data。
     *
     * @param log
     *            日志
     * @return 无变更也成功；svn 失败则 false
     */
    static boolean generate(ClientGdLog.Ui log) {
        File excelDir = PathConfig.excelDir();
        if (excelDir == null || !excelDir.isDirectory()) {
            log.line("Excel 目录不存在: " + (excelDir != null ? excelDir.getAbsolutePath() : "null"));
            return false;
        }
        SvnChangedXlsxPuller.ChangedLoadResult load = SvnChangedXlsxPuller.loadChangedXlsx(excelDir, log::line);
        if (!load.ok) {
            log.line("svn status 失败: " + excelDir.getAbsolutePath());
            return false;
        }
        List<ChangedXlsx> changed = load.files;
        if (changed.isEmpty()) {
            log.line("没有待生成的 Excel，跳过生成");
            log.status("生成跳过");
            return true;
        }
        log.line("待生成 " + changed.size() + " 个 Excel");
        File gdDir = PathConfig.gdDataDir();
        File limitDir = PathConfig.limitDir();
        if (!gdDir.isDirectory()) {
            log.line("服务器 GD 目录不存在: " + gdDir.getAbsolutePath());
            return false;
        }
        if (!limitDir.isDirectory()) {
            log.line("limit 目录不存在: " + limitDir.getAbsolutePath());
            return false;
        }
        AtomicBoolean cancel = new AtomicBoolean(false);
        int total = 0;
        for (int i = 0; i < changed.size(); i++) {
            File excel = changed.get(i).file;
            log.status("生成中 " + (i + 1) + "/" + changed.size());
            GdProgressContext ctx =
                new GdProgressContext(i + 1, changed.size(), excel.getName(), ClientGdLog.progressLog(log));
            GdBatchResult result = XlsxGdParallelProcessor.process(excel, ctx, cancel, limitDir, gdDir);
            if (!result.isSuccess()) {
                log.line("生成失败: " + excel.getName());
                return false;
            }
            int n = result.getWrittenSheetNames().size();
            total += n;
            log.line("已生成 " + n + " 个 GD: " + excel.getName());
        }
        log.line("生成完成，共 " + total + " 个 GD");
        log.status("生成完成");
        return true;
    }

    /**
     * GD/data 整包压成 gddata.zip。
     *
     * @param log
     *            日志
     * @return 成功
     */
    static boolean packZip(ClientGdLog.Ui log) {
        File src = PathConfig.gdDataDir();
        File zip = PathConfig.clientGdZip();
        if (!src.isDirectory()) {
            log.line("服务器 GD 目录不存在: " + src.getAbsolutePath());
            return false;
        }
        if (zip.isFile() && canNotWrite(zip, "客户端 zip", log)) {
            return false;
        }
        File zipDir = zip.getParentFile();
        if (zipDir != null && !zipDir.isDirectory()) {
            log.line("zip 目录不存在: " + zipDir.getAbsolutePath());
            return false;
        }
        try {
            log.line("打包 " + src.getAbsolutePath() + " -> " + zip.getAbsolutePath());
            int count = ZipArchiveModule.compressDirectory(src, zip, ZipArchiveModule.ZIP_CHARSET,
                ClientGdLog.zipListener(log, new ZipArchiveModule.ProgressThrottle()));
            log.line("已覆盖 zip，共 " + count + " 个文件");
            log.status("覆盖客户端完成");
            return true;
        } catch (Exception e) {
            log.line("覆盖客户端失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除 LocalLow 下全部 .gd。
     *
     * @param log
     *            日志
     * @return 成功
     */
    static boolean deleteLocalLow(ClientGdLog.Ui log) {
        File dir = PathConfig.clientLocalLowDataDir();
        if (!dir.isDirectory()) {
            log.line("LocalLow 不存在: " + dir.getAbsolutePath());
            return false;
        }
        List<File> gds = ClientGdLog.listGd(dir);
        if (gds == null) {
            log.line("无法列出 LocalLow: " + dir.getAbsolutePath());
            return false;
        }
        for (File file : gds) {
            if (canNotWrite(file, "LocalLow " + file.getName(), log)) {
                return false;
            }
        }
        int failed = 0;
        for (File file : gds) {
            if (!file.delete()) {
                log.line("删除失败: " + file.getAbsolutePath());
                failed++;
            }
        }
        log.line("共删除 " + (gds.size() - failed) + " 个 GD -> " + dir.getAbsolutePath());
        if (failed > 0) {
            log.line("另有 " + failed + " 个删除失败，请关闭客户端后重试");
            return false;
        }
        log.status("删除 LocalLow GD 完成");
        return true;
    }

    private static boolean canNotWrite(File file, String label, ClientGdLog.Ui log) {
        if (file == null || !file.isFile()) {
            log.line(label + " 不存在: " + (file != null ? file.getAbsolutePath() : "null"));
            return true;
        }
        try (RandomAccessFile ignored = new RandomAccessFile(file, "rw")) {
            return false;
        } catch (Exception e) {
            log.line(label + " 被占用，请关闭客户端后重试: " + file.getAbsolutePath());
            log.line("  " + e.getMessage());
            return true;
        }
    }
}
