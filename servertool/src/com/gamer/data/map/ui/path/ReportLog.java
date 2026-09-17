package com.gamer.data.map.ui.path;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.gamer.data.map.path.compare.PathSimulationReport;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.common.PathWorldPoint;

/**
 * 将寻路模拟报告格式化为调试日志文本。
 */
public final class ReportLog {

    private ReportLog() {}

    /**
     * @param reports
     *            模拟报告
     * @param startTime
     *            墙钟开始时间
     * @param endTime
     *            墙钟结束时间
     * @param startText
     *            已格式化起点
     * @param endText
     *            已格式化终点
     * @param compareMode
     *            是否为对比模式
     * @return 日志文本
     */
    public static String format(List<PathSimulationReport> reports, long startTime, long endTime, String startText,
        String endText, boolean compareMode) {
        StringBuilder log = new StringBuilder(8192);
        appendTime(log, startTime, endTime);
        appendCompareNote(log, compareMode);
        appendReports(log, reports);
        log.append("起点: ").append(startText).append('\n');
        log.append("终点: ").append(endText).append("\n\n");
        if (reports != null) {
            for (PathSimulationReport report : reports) {
                appendReportDetail(log, report);
            }
        }
        return log.toString();
    }

    private static void appendTime(StringBuilder log, long startTime, long endTime) {
        log.append("时间\n");
        log.append("开始: ").append(formatPathTime(startTime)).append('\n');
        log.append("结束: ").append(formatPathTime(endTime)).append('\n');
        log.append("墙钟耗时: ").append(endTime - startTime).append(" ms\n\n");
    }

    private static void appendCompareNote(StringBuilder log, boolean compareMode) {
        if (!compareMode) {
            return;
        }
        log.append("对比说明\n");
        log.append("当前模式对比同一次点击的世界坐标，Tool 与 World 按各自坐标规则换算。\n");
        log.append("对比模式不回放搜索展开点，搜索数量仅作为摘要指标。\n\n");
    }

    private static void appendReports(StringBuilder log, List<PathSimulationReport> reports) {
        log.append("结果对比\n");
        if (reports == null || reports.isEmpty()) {
            log.append("无结果\n\n");
            return;
        }
        for (PathSimulationReport report : reports) {
            appendReportSummary(log, report);
        }
        log.append('\n');
    }

    private static void appendReportSummary(StringBuilder log, PathSimulationReport report) {
        log.append(report.getName()).append(": ");
        log.append(report.isSuccess() ? "成功" : "失败");
        log.append(", 算法耗时=").append(report.getElapsedMillis()).append(" ms");
        if (report.getEstimatedMemoryBytes() >= 0L) {
            log.append(", 搜索数组估算=").append(PathSimulationReport.formatMemory(report.getEstimatedMemoryBytes()));
        }
        if (report.getExpandedCount() >= 0) {
            log.append(", 展开=").append(report.getExpandedCount());
        }
        log.append(", 路径格=").append(report.getPathResult() == null ? 0 : report.getPathResult().getGridPath().size());
        log.append(", 路径点=").append(report.getPathResult() == null ? 0 : report.getPathResult().getWorldPath().size());
        log.append(", 搜索=").append(report.getSearchCount());
        if (report.getMessage() != null && !report.getMessage().isEmpty()) {
            log.append(", 说明=").append(report.getMessage());
        }
        if (report.getPathResult() != null && report.getPathResult().getFailureDetail().isPresent()) {
            PathFailureDetail detail = report.getPathResult().getFailureDetail();
            log.append(", 卡点=").append(formatFailurePoint(detail));
            log.append(", 直接阻挡=").append(detail.getBlockReasons().size());
        } else if (report.getPathResult() != null && report.getPathResult().getBlockReason().isPresent()) {
            log.append(", 阻挡=").append(report.getPathResult().getBlockReason().toDisplayText());
        }
        log.append('\n');
    }

    private static void appendReportDetail(StringBuilder log, PathSimulationReport report) {
        PathResult result = report.getPathResult();
        log.append("==== ").append(report.getName()).append(" ====\n");
        if (result == null) {
            log.append("无结果\n\n");
            return;
        }
        appendThinkLog(log, result);
        appendFailureLog(log, result.getFailureDetail());
        appendWorldMoveLog(log, result.getWorldPath());
        appendGridMoveLog(log, result.getGridPath());
        log.append('\n');
    }

    private static void appendFailureLog(StringBuilder log, PathFailureDetail detail) {
        if (detail == null || !detail.isPresent()) {
            return;
        }
        log.append("失败诊断\n");
        log.append("最终卡点: ").append(formatFailurePoint(detail)).append('\n');
        if (detail.getBlockReasons().isEmpty()) {
            log.append("直接阻挡: 未识别，可能因搜索上限提前结束\n\n");
            return;
        }
        for (PathBlockReason reason : detail.getBlockReasons()) {
            log.append("直接阻挡: ").append(reason.toDisplayText()).append('\n');
        }
        log.append('\n');
    }

    private static String formatFailurePoint(PathFailureDetail detail) {
        if (detail.getStuckWorld() != null) {
            return formatWorldPoint(detail.getStuckWorld());
        }
        return formatPathPoint(detail.getStuckGrid());
    }

    private static void appendThinkLog(StringBuilder log, PathResult result) {
        log.append("思考摘要\n");
        List<PathGridPoint> searched = result.getSearchedGrids();
        if ((searched == null || searched.isEmpty()) && !result.getSearchedWorlds().isEmpty()) {
            log.append("世界坐标搜索点: ").append(result.getSearchedWorlds().size()).append('\n');
            log.append('\n');
            return;
        }
        if (searched == null || searched.isEmpty()) {
            log.append("未展开搜索节点\n\n");
            return;
        }
        log.append("记录规则: 前20个、每50个、最后1个\n");
        int firstCount = Math.min(20, searched.size());
        int lastIndex = searched.size() - 1;
        int lastPrintedIndex = -1;
        for (int i = 0; i < firstCount; i++) {
            appendThinkLogLine(log, searched, i);
            lastPrintedIndex = i;
        }
        for (int i = 49; i < lastIndex; i += 50) {
            if (i >= firstCount) {
                appendThinkLogLine(log, searched, i);
                lastPrintedIndex = i;
            }
        }
        if (lastPrintedIndex != lastIndex) {
            appendThinkLogLine(log, searched, lastIndex);
        }
        log.append('\n');
    }

    private static void appendThinkLogLine(StringBuilder log, List<PathGridPoint> searched, int index) {
        log.append('#').append(index + 1).append(" 当前: ").append(formatPathPoint(searched.get(index))).append('\n');
    }

    private static void appendWorldMoveLog(StringBuilder log, List<PathWorldPoint> path) {
        log.append("真实路径\n");
        if (path == null || path.isEmpty()) {
            log.append("无世界坐标路径\n\n");
            return;
        }
        if (path.size() == 1) {
            log.append("起终点相同: ").append(formatWorldPoint(path.get(0))).append("\n\n");
            return;
        }
        for (int i = 1; i < path.size(); i++) {
            log.append("step ").append(i).append(": ").append(formatWorldPoint(path.get(i - 1))).append(" -> ")
                .append(formatWorldPoint(path.get(i))).append('\n');
        }
        log.append('\n');
    }

    private static void appendGridMoveLog(StringBuilder log, List<PathGridPoint> path) {
        log.append("格子辅助\n");
        if (path == null || path.isEmpty()) {
            log.append("无格子路径\n");
            return;
        }
        if (path.size() == 1) {
            log.append("起终点同格: ").append(formatPathPoint(path.get(0))).append('\n');
            return;
        }
        for (int i = 1; i < path.size(); i++) {
            log.append("step ").append(i).append(": ").append(formatPathPoint(path.get(i - 1))).append(" -> ")
                .append(formatPathPoint(path.get(i))).append('\n');
        }
    }

    private static String formatPathTime(long time) {
        return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(time));
    }

    private static String formatPathPoint(PathGridPoint point) {
        if (point == null) {
            return "-";
        }
        return "格(" + point.getCol() + "," + point.getRow() + ") 世界(" + point.getWorldX() + ","
            + point.getWorldZ() + ")";
    }

    private static String formatWorldPoint(PathWorldPoint point) {
        if (point == null) {
            return "-";
        }
        return "世界(" + point.getWorldX() + "," + point.getWorldZ() + ")";
    }
}
