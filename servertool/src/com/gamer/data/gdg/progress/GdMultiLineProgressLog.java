package com.gamer.data.gdg.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamer.data.log.Log;

/**
 * GD 生成进度：文件总进度 + 仅展示已有状态的 Sheet 行；整块刷新，避免行 offset 错位与占位空行。
 */
public class GdMultiLineProgressLog implements Log {

    private final Log delegate;
    private final List<String> sheetOrder = new ArrayList<>();
    private final Map<String, String> sheetLineTexts = new HashMap<>();
    private String summaryText = "";

    public GdMultiLineProgressLog(Log delegate) {
        this.delegate = delegate;
    }

    /**
     * 新一轮 GD 生成前重置进度块（同步写入 View）。
     *
     * @param sheetNames
     *            本次合法 Sheet 列表
     */
    public void resetProgressLines(List<String> sheetNames) {
        sheetOrder.clear();
        sheetLineTexts.clear();
        if (sheetNames != null) {
            sheetOrder.addAll(sheetNames);
        }
        summaryText = GdProgressContext.TAG_PROGRESS + "[总进度] 准备中…";
        flushBlock(true);
    }

    /**
     * 更新文件级 Sheet 统计。
     */
    public void updateFileCounts(String fileName, int sheetTotal, int validateDone, int writeDone, int generateDone,
        boolean cancelled) {
        if (cancelled) {
            summaryText = GdProgressContext.TAG_PROGRESS + "[总进度] 已取消 — 请重新点击「生成 GD」";
        } else {
            summaryText = GdProgressContext.TAG_PROGRESS + "[总进度] " + fileName + "  Sheet共" + sheetTotal + "  校验完"
                + validateDone + "  写完" + writeDone + "  生成完" + generateDone;
        }
        flushBlock(false);
    }

    /**
     * 更新单 Sheet 行级进度。
     *
     * @param sheetName
     *            Sheet 名
     * @param rowsRead
     *            已处理有效数据行数（单遍流水线与校验/写同步）
     * @param rowsValidated
     *            已校验数据行数
     * @param rowsWritten
     *            已写入数据行数
     * @param totalRows
     *            数据行总数，&lt;0 表示尚未知
     */
    public void updateSheetMetrics(String sheetName, int rowsRead, int rowsValidated, int rowsWritten, int totalRows) {
        if (!sheetOrder.contains(sheetName)) {
            return;
        }
        String total = totalRows >= 0 ? String.valueOf(totalRows) : "?";
        String text = GdProgressContext.TAG_PROGRESS + "[" + sheetName + "]  读" + rowsRead + "行  校验" + rowsValidated
            + "/" + total + "行  写" + rowsWritten + "/" + total + "行";
        sheetLineTexts.put(sheetName, text);
        flushBlock(false);
    }

    private void flushBlock(boolean syncOnEdt) {
        if (!(delegate instanceof GdLineAwareLog)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (summaryText != null && !summaryText.isEmpty()) {
            sb.append(summaryText);
        }
        for (String sheet : sheetOrder) {
            String line = sheetLineTexts.get(sheet);
            if (line != null && !line.isEmpty()) {
                sb.append('\n');
                sb.append(line);
            }
        }
        ((GdLineAwareLog)delegate).rewriteProgressBlock(sb.toString(), syncOnEdt);
    }

    @Override
    public void logMessage(String message) {
        delegate.logMessage(message);
    }

    @Override
    public void logMessage(String message, boolean redShow) {
        delegate.logMessage(message, redShow);
    }

    /**
     * 支持进度块整体刷新的日志接口，由 View 实现。
     */
    public interface GdLineAwareLog extends Log {
        /**
         * @param blockText
         *            进度块全文（多行）
         * @param syncOnEdt
         *            后台线程调用时为 true 则阻塞至 EDT 写完
         */
        void rewriteProgressBlock(String blockText, boolean syncOnEdt);
    }
}
