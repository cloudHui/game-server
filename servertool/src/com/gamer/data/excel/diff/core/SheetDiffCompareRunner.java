package com.gamer.data.excel.diff.core;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gamer.data.excel.framework.BaseCheck;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.excel.XlsxOpcSession;
import com.gamer.data.gdg.util.GdPathUtil;

/**
 * 单 Sheet 的 Excel / GD 差异对比（同步）。
 * <p>
 * 有差异才写日志；无差异静默。
 * </p>
 */
public final class SheetDiffCompareRunner {

    private SheetDiffCompareRunner() {}

    /**
     * 同步对比单个 Sheet。
     *
     * @param cancelFlag
     *            取消标记，可为 null
     * @return true 有差异并已打日志；false 无差异；取消时 null
     */
    public static Boolean compareOneSheet(BaseCheck baseCheck, ExcelOperate excel, String sheetName,
        boolean compareExcel, boolean compareGd, AtomicBoolean cancelFlag, ProgressLog progressLog) {
        if (excel == null || sheetName == null || sheetName.isEmpty()) {
            return Boolean.FALSE;
        }
        if (isCancelled(cancelFlag)) {
            return null;
        }
        File curFile = excel.file;
        File srcFile = new File(baseCheck.XML_PATH, excel.fileName);
        XlsxOpcSession curSession = null;
        XlsxOpcSession srcSession = null;
        long totalStart = System.currentTimeMillis();
        try {
            ExcelLog excelLog = null;
            List<String> gdLines = null;
            long excelCostMs = 0L;
            long gdCostMs = 0L;

            if (compareExcel && curFile != null && curFile.exists()) {
                curSession = XlsxOpcSession.open(curFile);
                if (srcFile.exists()) {
                    srcSession = XlsxOpcSession.open(srcFile);
                }
                if (isCancelled(cancelFlag)) {
                    return null;
                }
                long t0 = System.currentTimeMillis();
                try {
                    excelLog = buildExcelLog(srcSession, curSession, sheetName);
                } catch (Exception ex) {
                    progressLog.log(excel.fileName + " / " + sheetName + " Excel 失败: " + ex.getMessage(), true);
                }
                excelCostMs = System.currentTimeMillis() - t0;
            }

            if (isCancelled(cancelFlag)) {
                return null;
            }

            if (compareGd) {
                long t0 = System.currentTimeMillis();
                gdLines = compareGdSafe(baseCheck, sheetName, progressLog);
                gdCostMs = System.currentTimeMillis() - t0;
            }

            if (isCancelled(cancelFlag)) {
                return null;
            }

            boolean excelDiff = excelLog != null;
            boolean gdDiff = gdLines != null && !gdLines.isEmpty();
            if (!excelDiff && !gdDiff) {
                return Boolean.FALSE;
            }
            progressLog.log(excel.fileName + " / " + sheetName, false);
            if (excelDiff) {
                progressLog.log("  Excel：" + excelLog.summary + "（" + formatCostMs(excelCostMs) + "）", false);
                logDiffLines(progressLog, excelLog.headerLines);
                logDiffLines(progressLog, excelLog.dataLines);
            }
            if (gdDiff) {
                progressLog.log("  GD：差异 " + gdLines.size() + " 行（" + formatCostMs(gdCostMs) + "）", false);
                logDiffLines(progressLog, gdLines);
            }
            progressLog.log("  合计 " + formatCostMs(System.currentTimeMillis() - totalStart), false);
            return Boolean.TRUE;
        } catch (Exception ex) {
            progressLog.log(excel.fileName + " / " + sheetName + " 对比失败: " + ex.getMessage(), true);
            return Boolean.FALSE;
        } finally {
            closeSession(srcSession);
            closeSession(curSession);
        }
    }

    /**
     * 只对比 GD（生成后用）；有差异打日志。
     *
     * @return 是否打出差异
     */
    public static boolean compareGdOnly(BaseCheck baseCheck, String sheetName, AtomicBoolean cancelFlag,
        ProgressLog progressLog) {
        if (sheetName == null || sheetName.isEmpty() || isCancelled(cancelFlag)) {
            return false;
        }
        long t0 = System.currentTimeMillis();
        List<String> gdLines = compareGdSafe(baseCheck, sheetName, progressLog);
        if (gdLines == null || gdLines.isEmpty() || isCancelled(cancelFlag)) {
            return false;
        }
        progressLog.log(sheetName + ".gd", false);
        progressLog.log("  GD：差异 " + gdLines.size() + " 行（" + formatCostMs(System.currentTimeMillis() - t0) + "）",
            false);
        logDiffLines(progressLog, gdLines);
        return true;
    }

    private static ExcelLog buildExcelLog(XlsxOpcSession srcSession, XlsxOpcSession curSession, String sheetName)
        throws Exception {
        SheetDiffComparator.ExcelCompareResult result = SheetDiffComparator.compareExcel(srcSession, curSession, sheetName);
        String summary = mergePartSummary(result.headerSummary(), result.data.isEmpty() ? "无" : result.data.summary());
        if ("无".equals(summary)) {
            return null;
        }
        return new ExcelLog(summary, result.headerLines, result.data.logLines);
    }

    private static String mergePartSummary(String left, String right) {
        boolean leftEmpty = left == null || left.isEmpty() || "无".equals(left) || "—".equals(left);
        boolean rightEmpty = right == null || right.isEmpty() || "无".equals(right) || "—".equals(right);
        if (leftEmpty && rightEmpty) {
            return "无";
        }
        if (leftEmpty) {
            return right;
        }
        if (rightEmpty) {
            return left;
        }
        return left + " " + right;
    }

    private static void logDiffLines(ProgressLog progressLog, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (String line : lines) {
            progressLog.log("    " + line, false);
        }
    }

    private static String formatCostMs(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        if (ms < 1000L) {
            return ms + " ms";
        }
        long sec = (ms + 500L) / 1000L;
        if (sec < 60) {
            return sec + " 秒";
        }
        return (sec / 60) + " 分 " + (sec % 60) + " 秒";
    }

    private static void closeSession(XlsxOpcSession session) {
        if (session != null) {
            session.close();
        }
    }

    private static boolean isCancelled(AtomicBoolean cancelFlag) {
        return cancelFlag != null && cancelFlag.get();
    }

    private static List<String> compareGdSafe(BaseCheck baseCheck, String sheetName, ProgressLog progressLog) {
        try {
            File srcGd = GdPathUtil.archiveGdFile(baseCheck.GD_PATH, sheetName);
            File currGd = GdPathUtil.currGdFile(baseCheck.CURR_DIR, sheetName);
            if (!currGd.exists()) {
                return Collections.emptyList();
            }
            File srcFile = srcGd.exists() ? srcGd : null;
            return SheetDiffComparator.compareGdFiles(srcFile, currGd);
        } catch (Exception ex) {
            progressLog.log("  GD：失败 " + ex.getMessage(), true);
            return null;
        }
    }

    private static final class ExcelLog {
        final String summary;
        final List<String> headerLines;
        final List<String> dataLines;

        ExcelLog(String summary, List<String> headerLines, List<String> dataLines) {
            this.summary = summary;
            this.headerLines = headerLines;
            this.dataLines = dataLines;
        }
    }

    /** 日志 */
    public interface ProgressLog {
        void log(String message, boolean red);
    }
}
