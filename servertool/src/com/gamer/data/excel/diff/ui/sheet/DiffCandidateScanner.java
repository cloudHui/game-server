package com.gamer.data.excel.diff.ui.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gamer.data.excel.diff.core.SheetDiffCompareRunner;
import com.gamer.data.excel.diff.util.SvnChangedXlsxPuller;
import com.gamer.data.excel.framework.BaseCheck;
import com.gamer.data.log.Log;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.progress.GdProgressContext;
import com.gamer.data.gdg.util.GdPathUtil;

/**
 * SVN 变更候选内容对比；差异只写日志。
 */
public final class DiffCandidateScanner {

    private final BaseCheck baseCheck;
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    public DiffCandidateScanner(BaseCheck baseCheck) {
        this.baseCheck = baseCheck;
    }

    public void cancel() {
        cancelFlag.set(true);
    }

    /** 加载/重加载：SVN 变更 ∩ 当前目录 → 比 Excel+GD；SVN 失败则降级全部。 */
    public void compareSvnCandidates(Map<String, ExcelOperate> fileMap, Log log) {
        cancelFlag.set(false);
        if (fileMap == null || fileMap.isEmpty()) {
            return;
        }
        List<ExcelOperate> targets = resolveCandidates(fileMap, log);
        if (targets.isEmpty()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        int diffSheets = 0;
        SheetDiffCompareRunner.ProgressLog progress = log::logMessage;
        for (ExcelOperate excel : targets) {
            if (cancelFlag.get()) {
                return;
            }
            diffSheets += compareOneExcelAllSheets(excel, progress, log);
        }
        if (diffSheets > 0) {
            log.logMessage("差异汇总： " + diffSheets + " 个 Sheet / 候选 " + targets.size() + " 个文件（"
                + GdProgressContext.formatElapsed(t0) + "）", false);
        }
    }

    /** 生成后：只对比已写出 Sheet 的 GD。 */
    public void compareWrittenGds(List<String> sheetNames, Log log) {
        cancelFlag.set(false);
        if (sheetNames == null || sheetNames.isEmpty()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        int diffSheets = 0;
        SheetDiffCompareRunner.ProgressLog progress = log::logMessage;
        for (String sheetName : sheetNames) {
            if (cancelFlag.get()) {
                return;
            }
            if (SheetDiffCompareRunner.compareGdOnly(baseCheck, sheetName, cancelFlag, progress)) {
                diffSheets++;
            }
        }
        if (diffSheets > 0) {
            log.logMessage("GD 差异汇总： " + diffSheets + " 个 Sheet（" + GdProgressContext.formatElapsed(t0) + "）",
                false);
        }
    }

    private List<ExcelOperate> resolveCandidates(Map<String, ExcelOperate> fileMap, Log log) {
        SvnChangedXlsxPuller.StatusLoadResult svn = SvnChangedXlsxPuller.loadXlsxStatusResult(baseCheck.XML_PATH);
        if (!svn.ok) {
            log.logMessage("SVN 状态失败，降级对比当前目录全部 Excel", true);
            return new ArrayList<>(fileMap.values());
        }
        List<ExcelOperate> targets = new ArrayList<>();
        for (Map.Entry<String, ExcelOperate> e : fileMap.entrySet()) {
            if (svn.statusByName.containsKey(e.getKey())) {
                targets.add(e.getValue());
            }
        }
        if (targets.isEmpty()) {
            log.logMessage("SVN 无变更文件落在当前目录，跳过内容对比", false);
        } else {
            log.logMessage("内容对比候选： " + targets.size() + " 个（SVN 变更 ∩ 当前目录）", false);
        }
        return targets;
    }

    private int compareOneExcelAllSheets(ExcelOperate excel, SheetDiffCompareRunner.ProgressLog progress, Log probeLog) {
        if (excel.probeNotSheetNames(probeLog)) {
            probeLog.logMessage("读取 Sheet 失败: " + excel.fileName, true);
            return 0;
        }
        List<String> sheetNames = excel.getConfigSheetNames();
        if (sheetNames == null || sheetNames.isEmpty()) {
            return 0;
        }
        int logged = 0;
        for (String sheetName : sheetNames) {
            if (cancelFlag.get()) {
                break;
            }
            boolean doExcel = excel.file != null && excel.file.exists();
            boolean doGd = GdPathUtil.currGdFile(baseCheck.CURR_DIR, sheetName).exists();
            if (!doExcel && !doGd) {
                continue;
            }
            Boolean outcome =
                SheetDiffCompareRunner.compareOneSheet(baseCheck, excel, sheetName, doExcel, doGd, cancelFlag, progress);
            if (outcome == null) {
                break;
            }
            if (outcome) {
                logged++;
            }
        }
        return logged;
    }
}
