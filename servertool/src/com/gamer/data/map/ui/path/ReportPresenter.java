package com.gamer.data.map.ui.path;

import java.util.ArrayList;
import java.util.List;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.path.compare.PathSimulationKind;
import com.gamer.data.map.path.compare.PathSimulationReport;
import com.gamer.data.map.path.display.CoordinateAdapter;
import com.gamer.data.map.path.world.WorldMapDisplayMode;

/**
 * 寻路模拟报告的界面展示适配。
 */
public final class ReportPresenter {

    private ReportPresenter() {}

    /**
     * 将报告转换到当前画布视角。
     *
     * @param reports
     *            原始模拟报告
     * @param displayMode
     *            当前显示方向
     * @param toolMapData
     *            Tool 源地图
     * @return 当前画布视角下的报告
     */
    public static List<PathSimulationReport> toDisplayReports(List<PathSimulationReport> reports,
        WorldMapDisplayMode displayMode, MapData toolMapData) {
        if (reports == null || reports.isEmpty() || displayMode != WorldMapDisplayMode.WORLD_RAW
            || toolMapData == null) {
            return reports;
        }
        List<PathSimulationReport> ret = new ArrayList<>();
        for (PathSimulationReport report : reports) {
            if (report != null && report.getKind() == PathSimulationKind.TOOL) {
                ret.add(report.withPathResult(CoordinateAdapter.toDisplayToolResult(report.getPathResult(),
                    displayMode, toolMapData.getHeight())));
            } else {
                ret.add(report);
            }
        }
        return ret;
    }

    public static PathSimulationReport selectPrimaryReport(List<PathSimulationReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        for (PathSimulationReport report : reports) {
            if (report.getKind() == PathSimulationKind.TOOL) {
                return report;
            }
        }
        return reports.get(0);
    }

    public static PathSimulationReport selectCompareReport(List<PathSimulationReport> reports,
        PathSimulationReport primaryReport) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        for (PathSimulationReport report : reports) {
            if (report != primaryReport && report.getKind() == PathSimulationKind.WORLD) {
                return report;
            }
        }
        return null;
    }

    public static boolean isAllSuccess(List<PathSimulationReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return false;
        }
        for (PathSimulationReport report : reports) {
            if (report == null || !report.isSuccess()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 状态栏短摘要；路径/搜索等细节只写日志区。
     */
    public static String buildSummary(List<PathSimulationReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return "结果: -";
        }
        StringBuilder builder = new StringBuilder("结果: ");
        if (reports.size() > 1) {
            builder.append(isAllSuccess(reports) ? "对比成功" : "对比有失败");
        } else {
            builder.append(reports.get(0).isSuccess() ? "成功" : "失败");
        }
        for (PathSimulationReport report : reports) {
            builder.append(" | ");
            builder.append(report.getKind() == PathSimulationKind.TOOL ? "Tool" : "World");
            builder.append(' ').append(report.getElapsedMillis()).append("ms");
        }
        return builder.toString();
    }
}
