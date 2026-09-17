package com.gamer.data.gdg.generate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.gamer.data.log.Log;
import com.gamer.data.limit.ColumnLengthPair;
import com.gamer.data.limit.LimitFileData;

import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.excel.GdHeartbeatRowHandler;
import com.gamer.data.gdg.excel.GdStreamRowHandler;
import com.gamer.data.gdg.excel.XlsxSheetStreamReader;
import com.gamer.data.gdg.progress.GdSheetProgressReporter;
import com.gamer.data.gdg.util.GdPathUtil;
import com.gamer.data.gdg.validate.GdCellValidator;
import com.gamer.data.gdg.validate.GdErrorCollector;
import com.gamer.data.gdg.validate.GdErrorMessage;
import com.gamer.data.gdg.validate.GdSheetSchema;
import com.gamer.data.gdg.validate.GdStreamRowValidate;
import com.gamer.data.gdg.validate.GdValidationAbortException;

/**
 * 单 Sheet 单遍流水线：读一行有效数据 → 校验 → 立即写 GD（按批刷新进度）。
 */
public class XlsxGdSheetPipeline {

    private XlsxGdSheetPipeline() {}

    /**
     * 处理单个 Sheet 并写入目标文件（通常为 .gd.tmp）。
     *
     * @param xlsxFile
     *            xlsx 文件
     * @param sheetName
     *            Sheet 名
     * @param limitData
     *            limit 完整配置，可为 null
     * @param collector
     *            整本 Excel 共享错误收集器
     * @param outFile
     *            输出临时文件
     * @param log
     *            日志
     * @param progress
     *            进度
     * @return 是否成功
     * @throws Exception
     *             读取或写入异常
     */
    public static boolean processSheet(File xlsxFile, String sheetName, LimitFileData limitData,
        GdErrorCollector collector, File outFile, Log log, GdSheetProgressReporter progress) throws Exception {
        if (outFile.exists() && !outFile.delete()) {
            throw new IllegalStateException("无法清理旧临时文件: " + outFile.getAbsolutePath());
        }
        PipelineRowHandler handler =
            new PipelineRowHandler(xlsxFile.getName(), sheetName, limitData, collector, outFile, progress);
        GdStreamRowHandler rowHandler = GdHeartbeatRowHandler.wrap(handler, log, "流水线 " + sheetName + "，");
        try {
            XlsxSheetStreamReader.readSheetRows(xlsxFile, sheetName, rowHandler, ExcelOperate.warnFromLog(log));
        } catch (RuntimeException e) {
            handler.abortWrite();
            if (e.getCause() instanceof GdValidationAbortException) {
                return false;
            }
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        } catch (Exception e) {
            handler.abortWrite();
            throw e;
        }
        if (collector.shouldStop() || collector.hasErrors()) {
            handler.abortWrite();
            return false;
        }
        if (!handler.finishWrite()) {
            handler.abortWrite();
            return false;
        }
        if (progress != null) {
            progress.setTotalRows(handler.getWrittenRowCount());
            progress.flushNow();
        }
        return true;
    }

    /**
     * 删除指定 Sheet 的正式 gd 与临时文件。
     *
     * @param workDir
     *            工作目录
     * @param sheetName
     *            Sheet 名
     */
    public static void deleteSheetOutputs(File workDir, String sheetName) {
        if (workDir == null || sheetName == null) {
            return;
        }
        GdPathUtil.currGdFile(workDir, sheetName).delete();
        GdPathUtil.currGdTempFile(workDir, sheetName).delete();
    }

    /**
     * 流水线行处理器。
     */
    private static final class PipelineRowHandler implements GdStreamRowHandler {
        private final String excelName;
        private final String sheetName;
        private final Map<String, String> limitTypes;
        private final List<ColumnLengthPair> limitPairs;
        private final GdErrorCollector collector;
        private final File outFile;
        private final GdSheetProgressReporter progress;
        private final List<String[]> headerRows = new ArrayList<>();
        private final Map<Integer, Integer> vindexFirstRow = GdStreamRowValidate.newVindexMap();
        private GdSheetSchema schema;
        private GdStreamWriteSession session;
        private int headerColumns;

        PipelineRowHandler(String excelName, String sheetName, LimitFileData limitData, GdErrorCollector collector,
            File outFile, GdSheetProgressReporter progress) {
            this.excelName = excelName;
            this.sheetName = sheetName;
            this.collector = collector;
            this.outFile = outFile;
            this.progress = progress;
            if (limitData == null) {
                this.limitTypes = null;
                this.limitPairs = null;
            } else {
                this.limitTypes = limitData.getSheetTypes(sheetName);
                this.limitPairs = limitData.getSheetPairs(sheetName);
            }
        }

        int getWrittenRowCount() {
            return session == null ? 0 : session.getWrittenRowCount();
        }

        void abortWrite() {
            if (session != null) {
                session.abort();
                session = null;
            }
            outFile.delete();
        }

        boolean finishWrite() throws Exception {
            if (headerRows.size() < 5 || schema == null) {
                collector.addError(GdErrorMessage.prependExcelFile(excelName, "Sheet(" + sheetName + ") 表头不完整"));
                return false;
            }
            if (session == null) {
                return false;
            }
            session.finish();
            return true;
        }

        @Override
        public void onFormulaCell(int rowNum0, int colIdx) {
            if (collector.shouldStop()) {
                throwAbort();
            }
            String colName = schema == null ? "" : schema.getColumnName(colIdx);
            String msg = GdErrorMessage.formatExcelErrorCell(sheetName, rowNum0 + 1, colName, "=");
            try {
                collector.addErrorOrAbort(GdErrorMessage.prependExcelFile(excelName, msg));
            } catch (GdValidationAbortException e) {
                throwAbort();
            }
        }

        @Override
        public void onRowEnd(int rowNum0, TreeMap<Integer, String> colValues) {
            if (collector.shouldStop()) {
                throwAbort();
            }
            if (rowNum0 < 5) {
                handleHeaderRow(rowNum0, colValues);
                return;
            }
            handleDataRow(rowNum0, colValues);
        }

        private void handleHeaderRow(int rowNum0, TreeMap<Integer, String> colValues) {
            if (rowNum0 == 0) {
                headerColumns = guessColumnsFromFirstRow(colValues);
            }
            String[] row = GdStreamRowValidate.toRowArray(colValues, headerColumns);
            headerRows.add(row);
            if (headerRows.size() == 5) {
                buildSchemaAfterHeader();
            }
        }

        private void buildSchemaAfterHeader() {
            try {
                schema = GdSheetSchema.build(sheetName, headerRows);
                session = GdStreamWriteSession.open(schema, outFile);
            } catch (Exception e) {
                collector.addError(GdErrorMessage.prependExcelFile(excelName, e.getMessage()));
            }
        }

        private void handleDataRow(int rowNum0, TreeMap<Integer, String> colValues) {
            if (schema == null || session == null) {
                return;
            }
            int excelRow = rowNum0 + 1;
            String[] row = GdStreamRowValidate.toRowArray(colValues, schema.getColumns());
            if (row.length == 0 || isBlank(row[0])) {
                return;
            }
            try {
                GdStreamRowValidate.validateDataRow(excelName, schema, row, excelRow, limitTypes, limitPairs,
                    vindexFirstRow, collector);
            } catch (GdValidationAbortException e) {
                throwAbort();
            }
            if (collector.shouldStop()) {
                throwAbort();
            }
            try {
                formatRowForWrite(row, excelRow);
                session.writeDataRow(row);
                if (progress != null) {
                    progress.onDataRowCommitted();
                }
            } catch (Exception e) {
                try {
                    collector.addErrorOrAbort(GdErrorMessage.prependExcelFile(excelName, e.getMessage()));
                } catch (GdValidationAbortException ex) {
                    throwAbort();
                }
            }
        }

        private void formatRowForWrite(String[] row, int excelRow) throws Exception {
            List<String[]> headRows = schema.getHeadRows();
            int cols = schema.getColumns();
            for (int colIdx = 0; colIdx < cols; colIdx++) {
                String value = row[colIdx];
                row[colIdx] = GdCellValidator.formatValueByType(sheetName, headRows, colIdx, value, excelRow);
            }
        }

        private int guessColumnsFromFirstRow(TreeMap<Integer, String> colValues) {
            int i = 0;
            while (true) {
                String v = colValues.get(i);
                if (v == null || v.isEmpty()) {
                    return i;
                }
                i++;
            }
        }

        private boolean isBlank(String v) {
            return v == null || v.trim().isEmpty();
        }

        private void throwAbort() {
            throw new RuntimeException(new GdValidationAbortException());
        }
    }
}
